# Build & test

- JDK 21 (bundled Android Studio JBR). Set `JAVA_HOME` first — the default
  shell may not have it:

  ```bash
  JAVA_HOME='C:\Program Files\Android\openjdk\jdk-21.0.8' ./gradlew.bat testDebugUnitTest assembleDebug
  ```

- Unit tests + debug APK land in `app/build/outputs/apk/debug/app-debug.apk`.
- Install on the emulator with `adb install -r` (never uninstall/clear data —
  that wipes the launcher layout). ADB: `%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe`.
- APKs for the user's phone go in the repo's `build/` folder (git-ignored),
  named `Vibiate-<versionName>-<short commit>.apk`, built from a clean,
  committed tree — not on the Desktop.
- Phone builds are release builds (`assembleRelease` →
  `app/build/outputs/apk/release/app-release.apk`): R8-minified,
  non-debuggable, and deliberately signed with this machine's debug keystore
  so they install as updates over earlier builds without losing the layout.
  The release build isn't debuggable, so `run-as` only works with the debug
  build installed; keep the debug build on the emulator for development.
