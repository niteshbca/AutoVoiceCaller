# Auto Voice Caller — physical SIM prototype

Kotlin Android application. No server, VoIP, Twilio, SIP, accessibility automation,
root access, external telephony hardware, or direct cellular uplink injection.

**This cannot guarantee that the customer hears the message.** It attempts:

`Android TTS → phone speaker → phone microphone → normal cellular SIM call`

Phone echo cancellation, noise suppression, audio focus and OEM audio routing can
remove the TTS from the microphone signal or silence TTS playback completely.
Turning the call speaker on does not guarantee that media/TTS uses that speaker.
Only a real call to a consenting test recipient can establish compatibility.
Do not rely on it for unattended or time-critical communication.

## What is included

- Complete Kotlin source, manifest, Android resources, Gradle configuration.
- Number queue, removal buttons, duplicate detection, message editor, Start/Stop.
- Post-speech delay 0–30 seconds (default 2); session limit 1–50 (default 10).
- Confirmation for every session; explicit large-queue confirmation for 5+ calls.
- Hindi (`hi-IN`) TTS at 0.9 speech rate; romanized Hindi pronunciation depends on
  the voice. For clearer Hindi, enter Devanagari text and install Hindi voice data.
- Default Phone app role for supported Telecom call state/control APIs.
- Manual fallback if the user declines the Phone role.
- In-call controls: answer incoming call, reject/end, speaker, mute/unmute,
  hold/unhold where supported, DTMF. Incoming calls stop the automated queue.
- Call notification with open/answer/end/stop actions. No automatic answering.
- Local draft persistence only (numbers, message, delay, limit); no call recordings,
  call audio files, contact permission, call-log permission, or analytics.
- GitHub Actions workflow that compiles, lints and provides the APK as an artifact.

## Why default Phone app permission is necessary

`TelephonyManager.CALL_STATE_OFFHOOK` includes **dialing, active and held** calls.
It is not evidence that a customer answered. Outgoing ringback is not necessarily
`CALL_STATE_RINGING` (that coarse state represents an incoming/waiting call).

Automatic mode uses `InCallService` and waits for **`Call.STATE_ACTIVE`**, never
OFFHOOK alone. Android binds that service for the selected default Phone app.
The user must explicitly accept Android's Phone-role dialog. This changes which
app handles incoming calls too; use a spare test phone and restore the original
Phone app afterward through Android Settings → Apps → Default apps → Phone app.

No custom SIM picker is implemented. `TelecomManager.placeCall()` is called
without a `PhoneAccountHandle`, so Android uses its normal/default calling choice.
If the device is configured to “Ask every time”, Android can show its own SIM
chooser. Set a default calling SIM in system settings for smoother testing.

## Supported versions

Minimum Android **8.0/API 26**; compile/target **Android 15/API 35**.
Designed for Android 8–15 physical phones with cellular voice calling. Newer
Android releases may install the APK, but have not been validated. No emulator
can validate the physical SIM/acoustic path. OEM/carrier restrictions still apply.

| Android versions | Implementation |
| --- | --- |
| 8–9 / API 26–28 | System default-dialer change prompt, InCallService/Call APIs, legacy PhoneStateListener, legacy Telecom speaker routing. |
| 10–11 / API 29–30 | RoleManager Phone-role request; InCallService active state and disconnect; legacy PhoneStateListener. |
| 12–13 / API 31–33 | TelephonyCallback for coarse IDLE/RINGING/OFFHOOK; InCallService remains authoritative for answer detection. Android 13 notification permission. |
| 14–15 / API 34–35 | CallEndpoint speaker selection via requestCallEndpointChange; no deprecated TelecomManager.endCall or hidden API. |

When no Phone role is granted, the system Phone app owns the call. This app opens
`ACTION_CALL`, observes coarse states, and **requires “Customer answered” manual
confirmation before TTS**. It requests speakerphone through AudioManager on a
best-effort basis, but you may need to enable Speaker in the system Phone app.
After TTS and delay, manually hang up in the system Phone app, then return and tap
Continue. It does not guess an answered time or automatically disconnect without
supported privileges. If call-state registration is denied, sessions are blocked.

## Build on a computer — no Android Studio needed

Prerequisites: JDK **17**, Android command-line SDK tools, internet for build
dependencies. Get Google's command-line tools from:
https://developer.android.com/studio#command-tools

Extract command-line tools into `YOUR_SDK/cmdline-tools/latest/` so that
`YOUR_SDK/cmdline-tools/latest/bin/sdkmanager` exists. Set `ANDROID_HOME` to
`YOUR_SDK`, and put the SDK tools and Java on PATH.

Linux/macOS example (replace SDK path):

```sh
export ANDROID_HOME="$HOME/Android/Sdk"
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
sdkmanager --licenses
sdkmanager 'platforms;android-35' 'build-tools;34.0.0' 'platform-tools'
cd AutoVoiceCaller
chmod +x gradlew
./gradlew --no-daemon assembleDebug lintDebug
```

The included `gradlew` is a small documented **Gradle bootstrap**, not the standard
binary wrapper. It downloads Gradle 8.9, verifies the distribution SHA-256, and
runs it. It requires curl, unzip-compatible Python 3 and JDK 17 on Unix.
Alternatively install Gradle 8.9 yourself and run `gradle assembleDebug lintDebug`.

Windows PowerShell example:

```powershell
$env:ANDROID_HOME = 'C:\Android\Sdk'
$env:Path = "$env:ANDROID_HOME\cmdline-tools\latest\bin;$env:ANDROID_HOME\platform-tools;$env:Path"
sdkmanager.bat --licenses
sdkmanager.bat 'platforms;android-35' 'build-tools;34.0.0' 'platform-tools'
cd AutoVoiceCaller
.\gradlew.bat --no-daemon assembleDebug lintDebug
```

The Windows bootstrap uses PowerShell to download/check/extract Gradle.
JDK 17 must already be available. A local `local.properties` file can instead
contain `sdk.dir=/absolute/path/to/sdk` (use escaped Windows backslashes or `/`).

Output: `app/build/outputs/apk/debug/app-debug.apk` — installable debug-signed APK.
Install with `adb install -r app/build/outputs/apk/debug/app-debug.apk` or copy it
to the phone and explicitly allow installation from your file manager.
Do not upload signing keys; release builds require your own signing configuration.

### Build using GitHub without Android Studio

1. Create a repository. Upload the **contents** of this project folder at the
   repository root, including `.github/workflows/build-apk.yml`.
2. Open Actions → Build Android APK → Run workflow (also runs on main/master push).
3. After success, download artifact `AutoVoiceCaller-debug-apk`, extract it,
   and install `app-debug.apk` on your phone.
4. No website hosting or backend deployment is required. GitHub is only a build
   machine; ordinary SIM minutes/charges and installed TTS voice requirements remain.

## First test and example flow

1. Use a physical Android phone with working SIM calling and installed Hindi TTS.
2. Grant call/phone-state permissions; grant notifications on Android 13+.
3. Tap Enable automatic mode and accept Android's default Phone app dialog.
4. First add **one colleague's consenting number**, then test whether they hear
   the complete TTS message. Start with Bluetooth/headsets disconnected and
   sensible call/media volume. This app does not force maximum volume.
5. For your example, add separately:
   - `9876543210`
   - `8765432109`
   - `9123456780`
6. Message is prefilled:
   “Namaste, main I-METICS Group se baat kar raha hoon. Aapko hamare software ke
   regarding information dene ke liye call kiya gaya hai.”
7. Delay = 2, maximum ≥ 3. Press Start Calling, review confirmation, accept.
8. Number 1 → normal SIM call → `STATE_ACTIVE` → speaker request → TTS →
   two-second delay → supported `Call.disconnect()` → removed call → number 2 →
   number 3 → Completed. **Keep this app visible** so subsequent calls can start.
9. If Android puts this app in the background, the next call is paused. Return and
   press Continue. There is no hidden background calling service or launch bypass.
10. Stop cancels TTS/timers/queue and requests disconnect for its own queue call.
    In manual mode, it explicitly asks you to end the current system-dialer call.

## Audio / AEC / NS limitations

AudioManager is used for manual speaker requests; automatic mode uses Telecom's
supported call-audio routing APIs, including Android 14+ CallEndpoint. TTS uses
speech content with media playback audio attributes. Call audio focus may prevent
this playback; acquiring/stealing call audio focus is not used as a workaround.

The app checks `AcousticEchoCanceler.isAvailable()` and
`NoiseSuppressor.isAvailable()` and displays capabilities. These public effects
attach to an **AudioRecord session owned by an app**. A normal third-party app
does not own the cellular microphone's recording session and cannot attach or
configure AEC/NS for the modem's uplink. Creating an AudioRecord solely to attach
effects would not process the cellular stream, would require an unnecessary
microphone permission, and could interfere with the call. Therefore no AudioRecord
or artificial session is created. Cellular AEC/NS stays under Android/OEM control
and may intentionally suppress the acoustic TTS signal. This requested feature
cannot be honestly implemented for the SIM uplink through public APIs.

## Permissions

| Permission | Reason |
| --- | --- |
| CALL_PHONE | Start a user-approved normal SIM call. Runtime permission. |
| READ_PHONE_STATE | Detect coarse call state and block queues while another call exists. Runtime permission. |
| MODIFY_AUDIO_SETTINGS | Best-effort supported audio settings/routing. Normal permission. |
| POST_NOTIFICATIONS | Android 13+ visible call controls. Automatic mode requires approval to avoid replacing the Phone app without notifications. |
| BIND_INCALL_SERVICE | A service protection attribute; only Android can bind. Not a runtime permission requested from the user. |

No RECORD_AUDIO, READ_CALL_LOG, READ_CONTACTS, ANSWER_PHONE_CALLS, INTERNET,
accessibility, overlay, full-screen-intent or background foreground-service
permission is requested. Phone-role approval provides supported InCallService
control. TTS engines may independently need internet to download voices; prefer
an installed offline voice for testing.

## Error behavior and safety

- Invalid numbers/USSD/short numbers are rejected from the queue. Emergency
  numbers are never automated; normal single-call dialing is separate.
- Permission/role loss, start exception or TTS failure stops the session.
- No answer has a 90-second limit; stalled TTS/session has a 180-second limit.
- Busy/rejected/no-network disconnect labels and codes are shown **when Telecom
  supplies them**. Carrier/OEMs may collapse them into a generic disconnect;
  coarse manual mode cannot reliably distinguish those failures.
- Disconnect before message completion or manually ended automatic call stops
  the queue; there are no automatic retries. Correct/review and start again.
- Speaker failures are displayed, with manual Speaker control where possible.
  Success only confirms routing acceptance, never customer hearing.
- Incoming/unrelated calls and hold stop the session so TTS cannot speak to a
  different call. Incoming answering is always explicit.
- Call end progression waits for removal of the owned call, never overlaps calls.
- App exit via Back cancels the queue; losing foreground pauses the next call.
  A running call's speech may complete while notification controls remain available.
- Force-stop/process death cannot guarantee hang-up: the existing cellular call
  may remain in the system dialer. Manually end it. Session state is not persisted
  or silently restarted; drafts are saved in app-private preferences.
- This is a dialer prototype, not a full production Phone app: no contact/history
  UI, conference merge UI or manufacturer-specific features. Restore the usual
  Phone app after testing. Test incoming calls and emergency-dialer handoff too.
- Calls must respect recipient consent and applicable telecom/spam rules.
  No number scraping, hidden campaigns or Android policy circumvention is present.

## Source organization

```
app/src/main/java/com/example/autovoicecaller/
  CallerApplication.kt       application/session setup
  MainActivity.kt            programmatic Android Views + Jetpack lifecycle UI
  NumberRules.kt             queue-number validation
  Session.kt                 in-memory queue/state coordinator
  SpeechEngine.kt            TTS initialization and tokenized callbacks
  PhoneMonitor.kt            modern/legacy coarse phone-state observers
  CallerInCallService.kt     Telecom Call state, routing, notification controls
  CallActionReceiver.kt      explicit notification actions
app/src/main/res/values/     strings and theme
app/src/main/AndroidManifest.xml
.github/workflows/build-apk.yml
```

UI uses Android Views built in Kotlin: no missing Compose or layout XML is needed.
MutableLiveData publishes state to lifecycle-aware UI observers. Queue generation
IDs invalidate delayed work after Stop; TTS utterance IDs reject stale callbacks.
Only the current verified outgoing call is automated.

## Device acceptance checklist

Use consenting test numbers. Check that no TTS runs during ringback, customer hears
all speech, two-second delay follows completion, and the next call waits until the
previous call ends. Repeat for all three numbers. Also test busy, reject, airplane
mode, manual hang-up, Stop during dialing/TTS/delay, incoming call interruption,
background pause, permission denial, denied Phone role, missing Hindi voice,
notification denial, role removal, process death, and Bluetooth/speaker failure.
A successful compilation does **not** prove the acoustic path works on your phone.

## Official API references

- https://developer.android.com/develop/connectivity/telecom/dialer-app
- https://developer.android.com/reference/android/telecom/Call
- https://developer.android.com/reference/android/telecom/InCallService
- https://developer.android.com/reference/android/telephony/TelephonyManager
- https://developer.android.com/reference/android/media/audiofx/AcousticEchoCanceler
- https://developer.android.com/reference/android/media/audiofx/NoiseSuppressor
