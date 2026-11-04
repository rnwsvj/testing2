# LOMOD PNLE Tracker - Android APK Project

This is a complete, native Android project that packages **https://lomod-pnle-study-tracker.replit.app** into a native Android APK application with auto-return support for **Clerk's Google Sign-In**.

## Features Included:
- **Clerk Google Sign-In Auto-Return**: 
  - Uses Android App Links (`https://lomod-pnle-study-tracker.replit.app`) with `singleTask` launch mode.
  - When Google Sign-In finishes in the redirected browser, Android automatically catches the URL and brings the user **straight back into the Android App**!
  - In-app Google OAuth popup handling (`onCreateWindow`) with clean Chrome User-Agent so Google never throws a `disallowed_useragent` error.
- Modern Android 14+ WebView container with hardware acceleration
- Swipe to refresh (`SwipeRefreshLayout`)
- Back button hardware navigation handler
- Screen WakeLock (keeps phone screen on during long study sessions)
- Offline detection & retry screen
- LocalStorage, DOM storage, and cookie support
- Dark theme matching the LOMOD PNLE styling

## How to Build the APK:

### Option 1: Open in Android Studio (Easiest)
1. Extract this ZIP file.
2. Open Android Studio.
3. Click **File > Open...** and select this extracted folder.
4. Wait for Gradle sync to finish (usually 1-2 minutes).
5. Go to **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
6. A notification will appear: "APK(s) generated successfully". Click **locate** to get your `app-debug.apk`!
7. Transfer `app-debug.apk` to your Android phone and install.

### Option 2: Command Line (Linux/macOS/Windows)
```bash
# Build debug APK
./gradlew assembleDebug

# Output APK location:
# app/build/outputs/apk/debug/app-debug.apk
```

### Option 3: Instant WebAPK / PWABuilder (No Android Studio Needed)
If you don't have Android Studio installed:
1. Open this app in Chrome on Android.
2. Tap "Install App" or "Add to Home Screen".
3. Chrome builds a native **WebAPK** automatically!
4. Or submit to https://www.pwabuilder.com to download a signed APK directly.
