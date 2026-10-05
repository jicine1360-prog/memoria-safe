# Memoria Emergency Protection App - Android

## Installation

### Prerequisites
- Android Studio Hedgehog or later
- JDK 17
- Android SDK 34 (API 34)
- Gradle 8.2+

### Build from Source

1. Clone the repository
   ```bash
   cd memoria
   ```

2. Build the project
   ```bash
   cd android
   ./gradlew build
   ```

3. Install on device
   ```bash
   ./gradlew installDebug
   ```

### Run Tests

```bash
./gradlew test
./gradlew connectedAndroidTest
```

## AndroidManifest Permissions

The app requires the following permissions:

### Core Permissions
- `CAMERA` - Optional screen capture (only when the user enables it)
- `RECORD_AUDIO` - Ambient audio evidence around a detection/SOS
- `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` - Location during emergency/lost mode
- `ACCESS_BACKGROUND_LOCATION` - Location sharing in lost mode
- `VIBRATE` - Haptic feedback
- `POST_NOTIFICATIONS` - Running indicator and emergency alerts
- `FOREGROUND_SERVICE` (+ location/microphone/mediaProjection/specialUse) - Running services
- `INTERNET` - Telegram Bot API delivery

The app does not request `CALL_PHONE` and does not place calls.

### Feature Requirements
- Camera (optional; screen capture is opt-in)
- Autofocus (not required)
- Accelerometer (required only for opt-in tap/shake detection)

## App Structure

### Main Screens
1. **MainActivity** - Main app interface with SOS button
2. **EmergencyActivity** - Emergency activation screen

### Key Features
- SOS button activation (triple-tap or 3-second hold)
- Real-time audio waveform visualization
- Status indicators (GPS, battery, network)
- Quick emergency contacts
- Event timeline with filtering
- Privacy mode toggle

## Themes

### Normal Theme
- Dark mode optimized
- Material Design 3 components
- Custom color palette for emergency states

### Emergency Theme
- Solid red background
- Full-screen overlay
- Alarm sound playback
- Camera preview
- Location sharing status

## Permissions Handling

The app requests permissions at runtime:

1. **Camera and Microphone** - For emergency recording
2. **Location** - For GPS tracking and sharing
3. **Notifications** - For emergency alerts
4. **Vibration** - For haptic feedback

## Accessibility

The app follows accessibility best practices:
- Large touch targets (150dp minimum)
- High contrast colors
- VoiceOver support
- TalkBack integration
- Dynamic text sizing

## ProGuard Rules

```
-keep class com.memoria.** { *; }
-dontwarn com.memoria.**
```

## Troubleshooting

### Build Issues
- Ensure JDK 17 is configured
- Update Android SDK to API 34
- Clean and rebuild project

### Permission Issues
- Check AndroidManifest.xml for required permissions
- Runtime permission handling in MainActivity

### Camera Issues
- Ensure camera hardware is available
- Check camera permissions in settings

## Support

For issues or questions, check the main README.md file in the project root.
