package com.iamsamk.preeesent;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
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
 *
 * Two modes:
 *  - Slides: Vol Up = next, Vol Down = back
 *  - Media:  Vol Up/Down = laptop volume, double Up = play/pause
 * What each volume press does (single and double) can be changed in Setup.
 */
public class MainActivity extends Activity {

    // ----- Actions a volume press can trigger -----
    // "mode" is handled on the phone; everything else is sent to the laptop.
    private static final String[] ACTION_IDS = {
            "none", "next", "prev", "start", "black", "end",
            "play_pause", "vol_up", "vol_down", "mute",
            "seek_fwd", "seek_back", "next_track", "prev_track", "fullscreen",
            "switch_app", "mode",
    };
    private static final String[] ACTION_LABELS = {
            "Nothing", "Next slide", "Previous slide", "Start slideshow", "Black screen", "End slideshow",
            "Play / Pause", "Volume +", "Volume −", "Mute",
            "Forward", "Rewind", "Next track", "Previous track", "Fullscreen",
            "Switch laptop app", "Switch Slides/Media mode",
    };

    private static final String SLIDES = "slides";
    private static final String MEDIA = "media";
    // Button slots: single press and double press of each volume key.
    private static final String[] SLOTS = {"up", "down", "up2", "down2"};

    private static String defaultAction(String mode, String slot) {
        if (SLIDES.equals(mode)) {
            switch (slot) {
                case "up": return "next";
                case "down": return "prev";
                case "up2": return "black";
                default: return "mode";
            }
        } else {
            switch (slot) {
                case "up": return "vol_up";
                case "down": return "vol_down";
                case "up2": return "play_pause";
                default: return "mode";
            }
        }
    }

    /** How long to wait for a second press before treating it as a single press. */
    private static final int DOUBLE_PRESS_MS = 350;

    private final ExecutorService net = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private EditText addressField;
    private EditText pinField;
    private CheckBox altTabBox;
    private TextView status;
    private TextView hint;
    private TextView mapTitle;
    private Button setupToggle;
    private Button modeSlidesBtn;
    private Button modeMediaBtn;
    private View setupPanel;
    private View slidesPanel;
    private View mediaPanel;
    private final Spinner[] mapSpinners = new Spinner[4];
    private SharedPreferences prefs;

    private String mode = SLIDES;
    private boolean setupOpen = false;

    // Double-press detection
    private int pendingKey = 0;
    private Runnable pendingSingle = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        // Keep the screen on so the volume buttons keep working mid-talk.
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        prefs = getSharedPreferences("preeesent", MODE_PRIVATE);
        addressField = findViewById(R.id.address);
        pinField = findViewById(R.id.pin);
        altTabBox = findViewById(R.id.altTabOnMode);
        status = findViewById(R.id.status);
        hint = findViewById(R.id.hint);
        mapTitle = findViewById(R.id.mapTitle);
        setupToggle = findViewById(R.id.setupToggle);
        modeSlidesBtn = findViewById(R.id.modeSlides);
        modeMediaBtn = findViewById(R.id.modeMedia);
        setupPanel = findViewById(R.id.setupPanel);
        slidesPanel = findViewById(R.id.slidesPanel);
        mediaPanel = findViewById(R.id.mediaPanel);
        mapSpinners[0] = findViewById(R.id.mapUp);
        mapSpinners[1] = findViewById(R.id.mapDown);
        mapSpinners[2] = findViewById(R.id.mapUp2);
        mapSpinners[3] = findViewById(R.id.mapDown2);

        addressField.setText(prefs.getString("address", ""));
        pinField.setText(prefs.getString("pin", ""));
        altTabBox.setChecked(prefs.getBoolean("altTabOnMode", false));
        altTabBox.setOnCheckedChangeListener((b, checked) ->
                prefs.edit().putBoolean("altTabOnMode", checked).apply());
        mode = prefs.getString("mode", SLIDES);

        setupMappingSpinners();

        // Header / setup
        setupToggle.setOnClickListener(v -> showSetup(!setupOpen));
        findViewById(R.id.scan).setOnClickListener(v -> scan());
        findViewById(R.id.connect).setOnClickListener(v -> connect());
        findViewById(R.id.resetMap).setOnClickListener(v -> resetMapping());

        // Mode switch
        modeSlidesBtn.setOnClickListener(v -> setMode(SLIDES, true));
        modeMediaBtn.setOnClickListener(v -> setMode(MEDIA, true));
        findViewById(R.id.switchApp).setOnClickListener(v -> send("switch_app"));

        // Slides buttons
        bind(R.id.next, "next");
        bind(R.id.prev, "prev");
        bind(R.id.start, "start");
        bind(R.id.black, "black");
        bind(R.id.end, "end");

        // Media buttons
        bind(R.id.playPause, "play_pause");
        bind(R.id.volUp, "vol_up");
        bind(R.id.volDown, "vol_down");
        bind(R.id.mute, "mute");
        bind(R.id.seekFwd, "seek_fwd");
        bind(R.id.seekBack, "seek_back");
        bind(R.id.nextTrack, "next_track");
        bind(R.id.prevTrack, "prev_track");
        bind(R.id.fullscreen, "fullscreen");

        setMode(mode, false);
        if (addressField.getText().toString().isEmpty()) {
            showSetup(true);
        } else {
            showSetup(false);
            connect();
        }
    }

    private void bind(int id, String action) {
        findViewById(id).setOnClickListener(v -> send(action));
    }

    // ----- Modes and setup screen -----

    private void setMode(String newMode, boolean fromUser) {
        boolean changed = !newMode.equals(mode);
        mode = newMode;
        prefs.edit().putString("mode", mode).apply();

        boolean slides = SLIDES.equals(mode);
        modeSlidesBtn.setBackgroundResource(slides ? R.drawable.btn_accent : R.drawable.btn);
        modeMediaBtn.setBackgroundResource(slides ? R.drawable.btn : R.drawable.btn_accent);
        updatePanels();
        loadMappingIntoSpinners();
        updateHint();

        if (fromUser && changed) {
            vibrate(new long[]{0, 60, 60, 60});
            setStatus(slides ? "Slides mode" : "Media mode");
            if (altTabBox.isChecked()) send("switch_app");
        }
    }

    private void showSetup(boolean open) {
        setupOpen = open;
        setupToggle.setText(open ? "Done" : "Setup");
        if (!open) hideKeyboard();
        updatePanels();
    }

    private void updatePanels() {
        boolean slides = SLIDES.equals(mode);
        setupPanel.setVisibility(setupOpen ? View.VISIBLE : View.GONE);
        slidesPanel.setVisibility(!setupOpen && slides ? View.VISIBLE : View.GONE);
        mediaPanel.setVisibility(!setupOpen && !slides ? View.VISIBLE : View.GONE);
    }

    // ----- Volume button mapping -----

    private String prefKey(String m, String slot) {
        return "map_" + m + "_" + slot;
    }

    private String mappedAction(String slot) {
        return prefs.getString(prefKey(mode, slot), defaultAction(mode, slot));
    }

    private static int indexOf(String id) {
        for (int i = 0; i < ACTION_IDS.length; i++) if (ACTION_IDS[i].equals(id)) return i;
        return 0;
    }

    private static String labelOf(String id) {
        return ACTION_LABELS[indexOf(id)];
    }

    private void setupMappingSpinners() {
        for (int i = 0; i < 4; i++) {
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                    android.R.layout.simple_spinner_item, ACTION_LABELS);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            mapSpinners[i].setAdapter(adapter);
            final String slot = SLOTS[i];
            mapSpinners[i].setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                    prefs.edit().putString(prefKey(mode, slot), ACTION_IDS[pos]).apply();
                    updateHint();
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) { }
            });
        }
    }

    private void loadMappingIntoSpinners() {
        mapTitle.setText(SLIDES.equals(mode)
                ? "Volume buttons in Slides mode" : "Volume buttons in Media mode");
        for (int i = 0; i < 4; i++) {
            mapSpinners[i].setSelection(indexOf(mappedAction(SLOTS[i])), false);
        }
    }

    private void resetMapping() {
        SharedPreferences.Editor e = prefs.edit();
        for (String slot : SLOTS) e.remove(prefKey(mode, slot));
        e.apply();
        loadMappingIntoSpinners();
        updateHint();
    }

    private void updateHint() {
        hint.setText("Vol ↑ " + labelOf(mappedAction("up"))
                + "  ·  Vol ↓ " + labelOf(mappedAction("down"))
                + "\n2× ↑ " + labelOf(mappedAction("up2"))
                + "  ·  2× ↓ " + labelOf(mappedAction("down2")));
    }

    // ----- Volume buttons -----

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode != KeyEvent.KEYCODE_VOLUME_UP && keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) {
            return super.onKeyDown(keyCode, event);
        }
        boolean up = keyCode == KeyEvent.KEYCODE_VOLUME_UP;
        String single = mappedAction(up ? "up" : "down");
        String dbl = mappedAction(up ? "up2" : "down2");

        if (event.getRepeatCount() > 0) {
            // Holding the key: keep changing the laptop volume, but nothing else.
            if ((single.equals("vol_up") || single.equals("vol_down"))
                    && event.getRepeatCount() % 3 == 0) {
                send(single, false);
            }
            return true;
        }

        if (pendingSingle != null && pendingKey == keyCode) {
            // Second press in time: it's a double press.
            handler.removeCallbacks(pendingSingle);
            pendingSingle = null;
            vibrate(new long[]{0, 30, 50, 30});
            run(dbl);
            return true;
        }
        if (pendingSingle != null) {
            // The other key was pressed: finish the earlier single press right away.
            handler.removeCallbacks(pendingSingle);
            Runnable r = pendingSingle;
            pendingSingle = null;
            r.run();
        }

        if ("none".equals(dbl)) {
            vibrate(new long[]{0, 30});
            run(single); // no double press set, so no need to wait
        } else {
            pendingKey = keyCode;
            pendingSingle = () -> {
                pendingSingle = null;
                vibrate(new long[]{0, 30});
                run(single);
            };
            handler.postDelayed(pendingSingle, DOUBLE_PRESS_MS);
        }
        return true; // consume so the phone volume doesn't change
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    private void run(String action) {
        if ("none".equals(action)) return;
        if ("mode".equals(action)) {
            setMode(SLIDES.equals(mode) ? MEDIA : SLIDES, true);
            return;
        }
        send(action, false);
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
            int code = request("GET", base + "/api/ping", pin, null);
            if (code == 200) {
                setStatus("Connected ✓  Volume buttons are live");
                runOnUiThread(() -> showSetup(false));
            } else if (code == 401) {
                setStatus("Wrong PIN. Scan the QR again");
            } else {
                setStatus("Can't reach laptop. Same Wi-Fi? Server running?");
            }
        });
    }

    private void send(String action) {
        send(action, true);
    }

    private void send(String action, boolean buzz) {
        if (buzz) vibrate(new long[]{0, 30});
        final String base = baseUrl();
        if (base == null) {
            setStatus("Enter your laptop's address first (tap Setup)");
            return;
        }
        final String pin = pinField.getText().toString().trim();
        final String label = labelOf(action);
        net.execute(() -> {
            int code = request("POST", base + "/api/action", pin,
                    "{\"action\":\"" + action + "\"}");
            if (code == 200) setStatus("Sent: " + label);
            else if (code == 401) setStatus("Wrong PIN. Scan the QR again");
            else if (code == 400) setStatus("Laptop app is out of date. Download the latest Preeesent");
            else setStatus("Can't reach laptop");
        });
    }

    private int request(String method, String url, String pin, String body) {
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
            return code;
        } catch (Exception e) {
            return -1;
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
        View focus = getCurrentFocus();
        if (imm != null && focus != null) {
            imm.hideSoftInputFromWindow(focus.getWindowToken(), 0);
            focus.clearFocus();
        }
    }

    @SuppressWarnings("deprecation")
    private void vibrate(long[] pattern) {
        Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (v != null && v.hasVibrator()) v.vibrate(pattern, -1);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        net.shutdownNow();
    }
}
