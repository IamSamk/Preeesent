# Preeesent

Use your phone as a presentation remote for your laptop.
**Volume Up = next slide, Volume Down = previous slide.**

Works with PowerPoint, Keynote, Google Slides, Canva, PDF viewers, anything that
uses the arrow keys.

```
 Phone (Android app or browser)  ──Wi-Fi──▶  Laptop (server.py)  ──▶  presses ← / →
```

## 1. Laptop

Needs Python 3.8+.

- **Windows:** double-click `laptop/start.bat`
- **Mac / Linux:** run `./laptop/start.sh`

It prints something like:

```
  Address : 192.168.1.20:8765
  PIN     : 4821
```

Mac only: the first time, allow your Terminal app under
**System Settings → Privacy & Security → Accessibility**, or keys won't be pressed.
If Windows Firewall asks, click **Allow** (private networks).

## 2. Phone

### Android app (volume buttons)

1. On your phone, open the repo's **Releases → latest** and download `Preeesent.apk`
   (it's built automatically by GitHub Actions on every push).
2. Open it and allow "install unknown apps" when asked.
3. Type the **Address** and **PIN** from the laptop, tap **Connect**.
4. Open your slides, start the slideshow, and press the volume buttons.

Keep the app open on screen while presenting (it keeps the screen awake for you).
Tick **Swap volume buttons** if you want them the other way round.

### Any phone (iPhone too) — browser

Open the address shown on the laptop (e.g. `http://192.168.1.20:8765`) in your phone
browser, or scan the QR code, enter the PIN, and use the big on-screen buttons.
Browsers don't let web pages read the volume buttons, so for those use the Android app.

## Buttons

| Button | Key pressed |
| --- | --- |
| Next / Volume Up | → |
| Back / Volume Down | ← |
| Start | F5 |
| Black | B (black screen) |
| End | Esc |

## Troubleshooting

- **Can't reach laptop:** phone and laptop must be on the same Wi-Fi. Some
  public/office Wi-Fi blocks devices from talking to each other; use your phone's
  hotspot and connect the laptop to it.
- **Wrong PIN:** a new PIN is made each time the server starts. Use
  `python server.py --pin 1234` to keep a fixed one.
- **Slides don't move:** click once on the slideshow window so it's in front.

## Building the Android app yourself

Open the `android/` folder in Android Studio and press Run, or:

```
cd android
./gradlew assembleDebug
```
