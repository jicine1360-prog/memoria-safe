# Memoria Safe Installation Guide (Android)

## 1. Requirements

- Android 8.0 (API 26) or later
- Internet connection (Wi-Fi or mobile data)
- **Telegram** app on recipient devices (guardians/family)

## 2. Install (sideload APK)

1. Transfer the provided `app-release.apk` to the phone (USB, cloud, messenger, etc.).
2. Tap the APK file in a file manager.
3. When asked to allow **install from unknown apps**, allow it.
   - Settings → Apps → Special access → **Install unknown apps** → allow your file manager/browser
4. Tap **Install** and wait for completion.
5. Open **Memoria Safe** from the home screen.

> Once a Play Store release is available, you can install it from the Play Store (not yet published).

## 3. First run and permissions

1. On the **Consent** screen, review what is collected, where it is sent, and retention,
   then tap **Agree and start**. If you decline, the app exits.
2. Grant permissions as requested:
   - **Location**: send location during emergencies
   - **Microphone**: ambient audio evidence around detection
   - **Camera**: screen capture (optional; only when enabled)
   - **Notifications**: running indicator and emergency alerts
3. For background location in lost mode, set **Location → Allow all the time**.

## 4. Create a Telegram bot (sender side)

1. Open **@BotFather** in Telegram.
2. Send `/newbot`.
3. Enter a bot **name** and a **username** (must end with `bot`).
4. Copy the issued **bot token**. Example: `123456789:AAExxxxxxxxxxxxxxxxxxxxxxxxxxx`

## 5. Find the recipient chat ID

1. Open a chat with your bot and send **any message once**.
2. Open the following URL in a browser (replace the token):
   ```
   https://api.telegram.org/bot<BOT_TOKEN>/getUpdates
   ```
3. The number in `"chat":{"id": 123456789 }` is the chat ID.
4. For multiple family members, have each one message the bot and collect their chat IDs.

> For privacy, never publish your bot token or chat IDs in chats or posts.

## 6. Configure recipients in the app

1. Open **Settings (⚙)** at the bottom right.
2. Under **Telegram**, enter the **bot token** and **recipient chat IDs** (comma-separated).
   Example: `111111111, 222222222`
3. Tap **Test** to confirm a test message arrives.

## 7. Verify it works

- **Danger signal (SOS)**: press and hold the red SOS button for about **3 seconds**.
  Recipients receive an alert and location, and evidence (about 10 s of audio, plus a
  screen capture if enabled) is sent.
- **Lost signal**: the guardian sends `/lost` to the bot to start location sharing, and
  `/stop` to stop it.

## 8. Build from source (developers)

Requirements: Android SDK 36, JDK 17+, Gradle 8.13 (bundled launcher)

```bash
cd android
./gradlew test
./gradlew assembleDebug            # debug APK
./gradlew bundleRelease            # AAB for Play (local signing required)
```

Release signing keys are not included. Create `android/keystore.properties` locally and
never commit the keystore or passwords.

## 9. Troubleshooting

| Symptom | Check |
|---|---|
| No notifications | Bot token / chat ID typos, recipient sent `/start`, internet connection |
| No location | Location permission (Allow all the time), GPS on |
| No audio evidence | Microphone permission, evidence enabled in settings |
| Lost mode not working | Guardian chat ID in the recipient list, `/lost` spelling |
| Install blocked | Allow unknown apps, file integrity |

## 10. Caution

- Memoria does not replace emergency services. In a real emergency, call your local
  emergency number directly (e.g., **911** in the US, **112** in the EU, **119/112** in Korea).
- While running, Android's microphone/location indicators remain visible; the app does not
  hide or disable them.
