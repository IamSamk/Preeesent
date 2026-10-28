# Preeesent

Use your phone as a presentation remote for your laptop.
**Volume Up = next slide, Volume Down = previous slide.**

Works with PowerPoint, Keynote, Google Slides, Canva, PDF viewers, anything that
uses the arrow keys.

```
 Phone (Android app or browser)  ──Wi-Fi──▶  Laptop (server.py)  ──▶  presses ← / →
```

## 1. Laptop

Needs Python 3.8+. It shows the address, PIN and a QR code to scan.

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
3. Tap **Scan QR on laptop** and point it at the QR code in the laptop window.
   It fills in the address and PIN and connects. (You can also type them and tap **Connect**.)
4. Open your slides, start the slideshow, and press the volume buttons.

Keep the app open on screen while presenting (it keeps the screen awake for you).

### Slides and Media modes

Switch with the **Slides / Media** tabs at the top, or **double-press Volume Down**.

| Volume button | Slides mode | Media mode (videos, music) |
| --- | --- | --- |
| Up | Next slide | Laptop volume + (hold to keep raising) |
| Down | Previous slide | Laptop volume − |
| Double Up | Black screen | Play / Pause |
| Double Down | Switch to Media | Switch to Slides |

Change any of these under **Setup → Volume buttons**. Options include mute, rewind/forward,
next/previous track, fullscreen and "Switch laptop app" (Alt+Tab, or Cmd+Tab on Mac).
Tick **"Changing mode also switches the laptop app"** to jump from your slides to the video
window automatically.

Media mode uses the laptop's media keys, so play/pause works with YouTube, VLC, Spotify,
Netflix and most players.

### Any phone (iPhone too) — browser

Open the address shown on the laptop (e.g. `http://192.168.1.20:8765`) in your phone
browser, or scan the QR code with your camera (the PIN is filled in for you), and use the big on-screen buttons.
Browsers don't let web pages read the volume buttons, so for those use the Android app.

## Buttons

| Button | Key pressed |
| --- | --- |
| Next / Back | → / ← |
| Start | F5 |
| Black | B (black screen) |
| End | Esc |
| Play / Pause, Vol +/−, Mute, Prev/Next track | Media keys |
| Rewind / Forward | ← / → |
| Full | F |
| ⇄ App | Alt+Tab (Cmd+Tab on Mac) |

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
