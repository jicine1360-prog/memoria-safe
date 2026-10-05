# Memoria Safe User Guide (Android)

Memoria Safe is a **personal-safety app that the device owner consents to and configures**.
It does not automatically call emergency services (112 etc.); in an emergency, contact local
emergency services directly.

## 1. Install and first run

1. Open the installed APK.
2. Review the consent screen (what is collected, where it is sent, retention) and tap
   **Agree and start**. If you decline, the app exits.
3. Grant camera, microphone, location, and notification permissions. Detection and capture
   run only when you enable them, and while running they are always shown by Android
   notifications.

## 2. Configure Telegram recipients

1. Open **Settings (⚙)** at the bottom right.
2. Under **Telegram**, enter your **bot token** and **recipient chat IDs** (comma-separated).
3. Tap **Test** to confirm the recipient receives a test message.
   (Email delivery is currently on hold.)

## 3. Danger signal (SOS)

- Press and hold the red **SOS button** in the center of the main screen for about
  **3 seconds** to activate.
- On activation, the configured recipients receive an **alert and location**, and about
  **10 seconds of ambient audio** (plus a **screen capture** if enabled) is sent as evidence.
- On the emergency screen you can choose **Cancel Emergency** or **Lock Emergency**.
- Pressing SOS runs the configured signal on the phone.

## 4. Lost signal

- When the guardian sends **`/lost`** to the bot, lost mode turns on and **location sharing**
  starts.
- **`/stop`** stops location sharing.
- While lost mode is on, a transparent "sharing location" screen is shown on the device.

## 5. Pattern detection (tap/shake)

- Tap/shake detection is **off by default** and runs only when you enable it in settings.
- The microphone is not recorded during normal use.

## 6. Data retention and deletion

- Evidence files stored on the device (audio, path, screen capture) are **automatically
  deleted 7 days after creation, when the app starts**.
- You can clear local data via app data deletion or by uninstalling the app.
- Deletion / contact: cloudseha@gmail.com · Telegram `@Memoriasafe_bot`

## 7. Caution

- While running, Android's microphone/location indicators remain visible; the app does not
  hide or disable them.
- Memoria does not replace emergency services. In a real emergency, call 112 (or your local
  emergency number) directly.
