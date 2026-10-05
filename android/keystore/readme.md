# Memoria Emergency Protection System - Keystore Configuration

# Android Keystore Configuration
# ==============================

## Location: app/keystore/release.keystore.properties

# IMPORTANT: Do NOT commit actual keystore files to version control
# This file contains configuration only - actual keystore is generated separately

# Keystore Configuration
keystore.path=release.keystore
keystore.password=${KEYSTORE_PASSWORD}
key.alias=memoria-release
key.password=${KEY_ALIAS_PASSWORD}

# Keystore Type
keystore.type=jks

# Build Configuration
android.injected.testOnly=false
android.useAndroidX=true
android.enableJetifier=true

## Build Commands:

# Generate keystore (run once, NOT in CI/CD):
# keytool -genkey -v -keystore release.keystore -alias memoria-release -keyalg RSA -keysize 2048 -validity 10000

# Build release APK:
# ./gradlew assembleRelease

# Sign APK (if needed):
# jarsigner -verbose -sigalg SHA256withRSA -digestalg SHA-256 \
#   -keystore release.keystore app-release-unsigned.apk memoria-release

## Environment Variables:
# KEYSTORE_PASSWORD=Your_keystore_password
# KEY_ALIAS_PASSWORD=Your_key_alias_password

# Store these in CI/CD secrets or secure vault, NOT in source code

# For GitHub Actions:
# Add to repository secrets:
# - ANDROID_KEYSTORE_PASSWORD
# - ANDROID_KEY_ALIAS
# - ANDROID_KEY_ALIAS_PASSWORD

# For Android Studio:
# File > Project Structure > Signing > Add release configuration
# Use environment variables for passwords
