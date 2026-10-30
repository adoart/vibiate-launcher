# Build & test

- JDK 21 (bundled Android Studio JBR). Set `JAVA_HOME` first — the default
  shell may not have it:

  ```bash
  JAVA_HOME='C:\Program Files\Android\openjdk\jdk-21.0.8' ./gradlew.bat testDebugUnitTest assembleDebug
  ```

- Unit tests + debug APK land in `app/build/outputs/apk/debug/app-debug.apk`.
- Install on the emulator with `adb install -r` (never uninstall/clear data —
  that wipes the launcher layout). ADB: `%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe`.
