#!/usr/bin/env python3
"""Preeesent laptop server.

Run this on the laptop that shows the slides. Your phone (the Preeesent
Android app, or any phone browser) sends commands here over Wi-Fi, and this
script presses the matching keys, so it works with PowerPoint, Keynote,
Google Slides, PDF viewers, etc.

    python server.py            # default port 8765
    python server.py --port 9000
"""

import argparse
import json
import os
import random
import socket
import sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

try:
    from pynput.keyboard import Controller, Key
    _keyboard = Controller()
except Exception as exc:  # no display, missing package, etc.
    _keyboard = None
    _keyboard_error = exc

HERE = os.path.dirname(os.path.abspath(__file__))

# Action name -> key(s) pressed on the laptop.
_APP_SWITCH = "cmd+tab" if sys.platform == "darwin" else "alt+tab"
ACTIONS = {
    # Slides
    "next": "right",
    "prev": "left",
    "start": "f5",         # start slideshow (PowerPoint / Google Slides)
    "start_current": "shift+f5",  # start from current slide (PowerPoint)
    "end": "esc",
    "black": "b",          # black screen toggle
    "white": "w",          # white screen toggle
    "first": "home",
    "last": "end",
    # Media (YouTube, VLC, Spotify, Netflix, Windows/Mac players...)
    "play_pause": "media_play_pause",
    "vol_up": "media_volume_up",
    "vol_down": "media_volume_down",
    "mute": "media_volume_mute",
    "next_track": "media_next",
    "prev_track": "media_previous",
    "seek_fwd": "right",
    "seek_back": "left",
    "fullscreen": "f",
    # Any mode
    "switch_app": _APP_SWITCH,
}


def press(combo):
    if _keyboard is None:
        print(f"[dry run] would press {combo}")
        return
    keys = []
    for part in combo.split("+"):
        keys.append(getattr(Key, part) if hasattr(Key, part) and len(part) > 1 else part)
    for k in keys:
        _keyboard.press(k)
    for k in reversed(keys):
        _keyboard.release(k)


def local_ip():
    """Best guess at this laptop's Wi-Fi address."""
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(("10.255.255.255", 1))  # no packet is sent
        return s.getsockname()[0]
    except OSError:
        return "127.0.0.1"
    finally:
        s.close()


def print_qr(text):
    try:
        import qrcode
    except ImportError:
        return
    qr = qrcode.QRCode(border=1)
    qr.add_data(text)
    qr.print_ascii(invert=True)


class Handler(BaseHTTPRequestHandler):
    pin = "0000"

    def log_message(self, fmt, *args):
        pass  # keep the console quiet; actions are printed below

    def _send(self, code, body, ctype="application/json"):
        data = body.encode() if isinstance(body, str) else body
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(data)

    def _pin_ok(self):
        return self.headers.get("X-Pin", "") == self.pin

    def do_GET(self):
        path = self.path.split("?")[0]
        if path in ("/", "/index.html"):
            with open(os.path.join(HERE, "remote.html"), "rb") as f:
                self._send(200, f.read(), "text/html; charset=utf-8")
        elif path == "/api/ping":
            if self._pin_ok():
                self._send(200, json.dumps({"ok": True, "name": socket.gethostname()}))
            else:
                self._send(401, json.dumps({"ok": False, "error": "wrong PIN"}))
        else:
            self._send(404, json.dumps({"ok": False}))

    def do_POST(self):
        if self.path.split("?")[0] != "/api/action":
            return self._send(404, json.dumps({"ok": False}))
        if not self._pin_ok():
            return self._send(401, json.dumps({"ok": False, "error": "wrong PIN"}))
        try:
            length = int(self.headers.get("Content-Length", 0))
            action = json.loads(self.rfile.read(length) or b"{}").get("action")
        except (ValueError, AttributeError):
            action = None
        if action not in ACTIONS:
            return self._send(400, json.dumps({"ok": False, "error": "unknown action"}))
        press(ACTIONS[action])
        print(f"  -> {action}")
        self._send(200, json.dumps({"ok": True}))


def main():
    parser = argparse.ArgumentParser(description="Preeesent laptop server")
    parser.add_argument("--port", type=int, default=8765)
    parser.add_argument("--pin", help="4-digit PIN (random if not given)")
    args = parser.parse_args()

    Handler.pin = args.pin or f"{random.randint(0, 9999):04d}"
    ip = local_ip()
    url = f"http://{ip}:{args.port}"

    server = ThreadingHTTPServer(("0.0.0.0", args.port), Handler)
    print("\n  Preeesent is running\n")
    print(f"  Address : {ip}:{args.port}")
    print(f"  PIN     : {Handler.pin}")
    print(f"  Browser : {url}\n")
    print("  Scan this with the Preeesent app (or your phone camera):\n")
    print_qr(f"{url}/#pin={Handler.pin}")
    if _keyboard is None:
        print(f"  WARNING: can't press keys ({_keyboard_error}). Running in dry-run mode.")
        print("  Install with: pip install -r requirements.txt\n")
    print("  Phone and laptop must be on the same Wi-Fi. Ctrl+C to stop.\n")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nStopped.")
        sys.exit(0)


if __name__ == "__main__":
    main()
