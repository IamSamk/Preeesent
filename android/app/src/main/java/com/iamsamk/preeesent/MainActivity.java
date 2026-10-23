package com.iamsamk.preeesent;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Vibrator;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Phone remote for the Preeesent laptop server.
 * Volume Up = next slide, Volume Down = previous slide (swappable).
 */
public class MainActivity extends Activity {

    private final ExecutorService net = Executors.newSingleThreadExecutor();

    private EditText addressField;
    private EditText pinField;
    private CheckBox swapBox;
    private TextView status;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        // Keep the screen on so the volume buttons keep working mid-talk.
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        prefs = getSharedPreferences("preeesent", MODE_PRIVATE);
        addressField = findViewById(R.id.address);
        pinField = findViewById(R.id.pin);
        swapBox = findViewById(R.id.swap);
        status = findViewById(R.id.status);

        addressField.setText(prefs.getString("address", ""));
        pinField.setText(prefs.getString("pin", ""));
        swapBox.setChecked(prefs.getBoolean("swap", false));
        swapBox.setOnCheckedChangeListener((b, checked) ->
                prefs.edit().putBoolean("swap", checked).apply());

        findViewById(R.id.scan).setOnClickListener(v -> scan());
        findViewById(R.id.connect).setOnClickListener(v -> connect());
        findViewById(R.id.next).setOnClickListener(v -> send("next"));
        findViewById(R.id.prev).setOnClickListener(v -> send("prev"));
        findViewById(R.id.start).setOnClickListener(v -> send("start"));
        findViewById(R.id.black).setOnClickListener(v -> send("black"));
        findViewById(R.id.end).setOnClickListener(v -> send("end"));

        if (!addressField.getText().toString().isEmpty()) connect();
    }

    // ----- QR scan -----

    private void scan() {
        new IntentIntegrator(this)
                .setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
                .setPrompt("Point at the QR code on your laptop")
                .setOrientationLocked(false)
                .setBeepEnabled(false)
                .initiateScan();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (result == null) {
            super.onActivityResult(requestCode, resultCode, data);
            return;
        }
        if (result.getContents() == null) return; // cancelled
        // QR looks like: http://192.168.1.20:8765/#pin=4821
        Uri uri = Uri.parse(result.getContents().trim());
        if (uri.getHost() == null || !"http".equals(uri.getScheme())) {
            setStatus("That QR code isn't from Preeesent");
            return;
        }
        int port = uri.getPort() == -1 ? 8765 : uri.getPort();
        addressField.setText(uri.getHost() + ":" + port);
        String frag = uri.getFragment();
        if (frag != null && frag.startsWith("pin=")) pinField.setText(frag.substring(4));
        connect();
    }

    // ----- Volume buttons -----

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (event.getRepeatCount() == 0) { // ignore auto-repeat when held
                boolean up = keyCode == KeyEvent.KEYCODE_VOLUME_UP;
                if (swapBox.isChecked()) up = !up;
                send(up ? "next" : "prev");
            }
            return true; // consume so the volume doesn't change
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    // ----- Networking -----

    private String baseUrl() {
        String addr = addressField.getText().toString().trim();
        if (addr.isEmpty()) return null;
        if (!addr.startsWith("http://") && !addr.startsWith("https://")) addr = "http://" + addr;
        // Add the default port if the user typed only an IP.
        String hostPart = addr.substring(addr.indexOf("//") + 2);
        if (!hostPart.contains(":")) addr = addr + ":8765";
        if (addr.endsWith("/")) addr = addr.substring(0, addr.length() - 1);
        return addr;
    }

    private void connect() {
        prefs.edit()
                .putString("address", addressField.getText().toString().trim())
                .putString("pin", pinField.getText().toString().trim())
                .apply();
        hideKeyboard();
        final String base = baseUrl();
        if (base == null) {
            setStatus("Enter your laptop's address first");
            return;
        }
        final String pin = pinField.getText().toString().trim();
        setStatus("Connecting…");
        net.execute(() -> {
            Result r = request("GET", base + "/api/ping", pin, null);
            if (r.code == 200) setStatus("Connected ✓  Volume buttons are live");
            else if (r.code == 401) setStatus("Wrong PIN");
            else setStatus("Can't reach laptop. Same Wi-Fi? Server running?");
        });
    }

    private void send(String action) {
        vibrate();
        final String base = baseUrl();
        if (base == null) {
            setStatus("Enter your laptop's address first");
            return;
        }
        final String pin = pinField.getText().toString().trim();
        net.execute(() -> {
            Result r = request("POST", base + "/api/action", pin,
                    "{\"action\":\"" + action + "\"}");
            if (r.code == 200) setStatus("Sent: " + action);
            else if (r.code == 401) setStatus("Wrong PIN");
            else setStatus("Can't reach laptop");
        });
    }

    private static final class Result {
        final int code;
        Result(int code) { this.code = code; }
    }

    private Result request(String method, String url, String pin, String body) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setRequestMethod(method);
            c.setConnectTimeout(2000);
            c.setReadTimeout(3000);
            c.setRequestProperty("X-Pin", pin);
            if (body != null) {
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/json");
                c.setFixedLengthStreamingMode(bytes.length);
                try (OutputStream os = c.getOutputStream()) { os.write(bytes); }
            }
            int code = c.getResponseCode();
            InputStream is = code < 400 ? c.getInputStream() : c.getErrorStream();
            if (is != null) is.close();
            return new Result(code);
        } catch (Exception e) {
            return new Result(-1);
        } finally {
            if (c != null) c.disconnect();
        }
    }

    // ----- Helpers -----

    private void setStatus(String text) {
        runOnUiThread(() -> status.setText(text));
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null && getCurrentFocus() != null) {
            imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
            getCurrentFocus().clearFocus();
        }
    }

    @SuppressWarnings("deprecation")
    private void vibrate() {
        Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (v != null && v.hasVibrator()) v.vibrate(30);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        net.shutdownNow();
    }
}
