# Build verification

Verified on 30 September 2026 with JDK 17, Gradle 8.9, Android SDK 35,
Android Gradle Plugin 8.7.3 and Kotlin 2.0.21.

- `assembleDebug lintDebug`: BUILD SUCCESSFUL.
- Android lint: 0 errors; 11 warnings (pinned dependency versions,
  translation/localization recommendations and required telephony feature).
- `apksigner verify`: PASS; APK Signature Scheme v2, one debug signer.
- `apk/AutoVoiceCaller-debug.apk` is the compiled, installable debug build
  from the source in this archive.

No real SIM phone was connected during development. Customer answer detection,
OEM speaker routing, acoustic TTS transmission, incoming-call presentation and
call queues require the real-device acceptance checklist in README.md. Build
success and signature verification do not establish customer audio quality.

The debug APK is for testing. Updates built on another computer may use a
separate debug signing key; uninstall the old test build first if Android
reports a signature mismatch (this clears saved drafts).
