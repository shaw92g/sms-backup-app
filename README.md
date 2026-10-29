# SMS Backup for Android

Native Java Android app (Android 8.0/API 26 or newer) that uploads received SMS
as daily UTF-8 JSON to your HTTPS server at 1 AM in the phone's local time zone.
No server URL or credentials are hardcoded.

Debug builds support both HTTP and HTTPS endpoints for testing. Release builds
require HTTPS. HTTP sends SMS contents and bearer tokens without encryption;
use it only with trusted test endpoints and disposable messages.

## Sandboxed Docker Environment

Install and start Docker Desktop for macOS. No host JDK, Gradle, or Android SDK
is needed for this option. Allow at least 6 GB of memory for Docker Desktop and
several GB of disk space for the toolchain and dependency cache.

Review the [Android SDK license terms](https://developer.android.com/studio/terms).
If you accept them, build the image with explicit consent:

```sh
ANDROID_SDK_LICENSES_ACCEPTED=true docker compose --progress plain build android
docker compose run --rm android
```

The default command runs the core and JSON unit tests, builds the debug APK,
and runs Android Lint. Outputs are exported to `artifacts/apk/app-debug.apk` and
`artifacts/reports/`. Failed lint checks still export any generated reports and
APK, while preserving the failing exit code. The image contains JDK 17, Android
SDK Platform 35, build tools 35.0.0, platform tools, and pinned command-line tools.
The project Gradle wrapper downloads Gradle into a container-only named volume.

Additional commands:

```sh
docker compose run --rm android assembleDebug
docker compose run --rm android lintDebug
docker compose run --rm android shell
```

The shell starts inside the sandbox with a writable copy of the project. Its
changes are temporary; edit source in VS Code on the host. A fresh copy is made
for each container run. From the shell you can run `./gradlew` and use `adb`.
Only files explicitly copied into `/output` survive shell exit, apart from the
Gradle cache and Android state volumes.

Isolation defaults:

- Non-root user, read-only container filesystem, all Linux capabilities dropped,
  and privilege escalation disabled.
- Source is bind-mounted **read-only**, then copied to a temporary workspace;
  Gradle cannot modify the host source or its local build configuration.
- Only `artifacts/` is writable on the host. Dependencies and debug signing keys
  persist in dedicated Docker volumes, not in your host home directory.
- No Docker socket, host home directory, privileged mode, USB devices, or host
  networking are exposed. Standard container networking remains enabled for
  dependency downloads; this is not an air-gapped environment.
- CPU, memory, process, and temporary filesystem limits are applied. This is a
  development sandbox, not a guarantee against malicious build dependencies.

The container uses `linux/amd64` because Android's Linux build tools require
x86-64. Apple Silicon Macs run it through Docker Desktop emulation, which can
make builds slower. This image is a **headless build/development environment**,
not an Android Studio GUI or accelerated emulator. Docker Desktop on macOS
cannot directly pass through a phone over USB. Use Android wireless debugging
(`adb pair`, then `adb connect` with your phone's displayed IP/ports) inside the
shell, or install the exported APK using host Android tools. Never expose ADB
ports to untrusted networks.

To remove persistent sandbox caches and debug signing keys, run
`docker compose down --volumes`. This leaves exported artifacts untouched but
changes the debug signing identity on the next build; existing phone installs
may need to be uninstalled before installing a newly signed APK.

### Verified Setup

The image was built and run on Docker Desktop with an ARM64 host using amd64
emulation. Core/JSON tests, `assembleDebug`, and `lintDebug` pass. The APK is exported
to `artifacts/apk/app-debug.apk`. Lint currently reports five non-blocking
warnings covering synchronous preferences, dependency freshness, receiver action
validation, and localization; reports are exported alongside the APK.

Runtime checks confirmed UID 1000, zero effective capabilities,
`NoNewPrivs: 1`, read-only source/SDK, writable temporary workspace/output, and
no Docker socket. The workspace explicitly allows execution for Gradle tools;
`/tmp` remains non-executable. Container builds disable Gradle filesystem
watching because the workspace is disposable and native watching can fail
under amd64 emulation. Device-level SMS and alarm behavior has not been tested
by these build checks.

## Local Build and Install

1. Open this directory in Android Studio and use its bundled JDK (17 or newer).
2. Install Android SDK Platform 35 and the SDK build tools when prompted.
3. Sync Gradle, connect a phone or emulator, and run the `app` configuration.
   Alternatively, set `ANDROID_HOME` to your SDK directory and run:

   ```sh
   ./gradlew assembleDebug
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

The Gradle wrapper is included. Java 17+ and internet access are required for
the first dependency download. Never commit signing keys, tokens, or real SMS.

## Configure

1. Enter your server endpoint and optional bearer token. Debug builds accept
  HTTP or HTTPS; release builds require HTTPS.
2. Tap **Save & enable**, accept the data disclosure, and grant SMS permission.
3. On Android 12+, open **Alarms & reminders** and allow exact alarms for the
   closest possible 1 AM trigger. Without it, the app uses an inexact alarm.
4. Optionally enter a **Backup date** as `YYYY-MM-DD` or select it with the
  calendar picker, then tap **Upload SMS**. Leave the date blank to upload
  yesterday in the phone's local time zone. Invalid dates are rejected.
  This date applies only to manual uploads; nightly backups are unchanged.
5. Turn off **Nightly backup** to cancel queued work and future alarms. An HTTP
   request already accepted by your server cannot be recalled.

The app has no SMS sending or deletion permissions. It reads only received
messages still present in the SMS inbox, not sent/draft messages, MMS, or RCS.
Messages deleted before export cannot be backed up. A day without messages
still produces JSON containing the date, time zone, and an empty `messages` array.

## Server Contract

Each day is a separate **POST with a UTF-8 JSON object as the request body**,
not multipart form data. Use a valid, trusted TLS certificate
outside debug-only HTTP testing.

```http
POST /sms HTTP/1.1
Content-Type: application/json; charset=utf-8
Content-Disposition: attachment; filename="sms-2026-10-26.json"
Authorization: Bearer <your-token>
Idempotency-Key: <per-install-uuid>:2026-10-26
X-Backup-Date: 2026-10-26
X-Backup-Timezone: America/New_York
```

`Authorization` is omitted when no token is configured. Example JSON body:

```json
{
  "backup_date": "2026-10-26",
  "timezone": "America/New_York",
  "messages": [
    {
      "sms_id": "42",
      "address": "+15551234567",
      "received_at": "2026-10-26T08:00-04:00",
      "received_at_epoch_ms": 1793016000000,
      "body": "First line\nSecond line with \"quotes\""
    }
  ]
}
```

Newlines, carriage returns, tabs, quotes, backslashes, and control characters
are JSON-escaped; a standard JSON parser reconstructs the original message
body, including Unicode. Receipt timestamps in milliseconds are numbers, and
unavailable sender addresses or message bodies are `null`.
`sms_id` stays a string. `received_at` is an ISO-8601 offset timestamp.
Date selection uses receipt time with an inclusive local midnight and exclusive
next local midnight, including DST changes. Export is streamed to a private
temporary file rather than loading all messages into memory.

This replaces the previous CSV contract. Update your receiver to accept JSON
before using this app version. Date/time-zone and idempotency headers are
unchanged; existing server records for a day's key still count as duplicates.

Return any **2xx only after durable storage**. Return 200/204 for an already
stored idempotency key. The server must atomically deduplicate by
`Idempotency-Key`: an upload can be retried after the server saves it but before
the phone records success. A manual upload shares the automatic upload's key.
Reinstalling or clearing app storage generates a new install ID.

408, 429, 5xx, and transport/export I/O errors use exponential WorkManager
backoff, starting at 30 seconds. Other responses (including redirects) stop
that attempt and are shown in Activity; the next alarm or opening the app will
attempt the pending day again. Correct the URL or credentials before retrying.
Redirects are intentionally not followed, so bearer credentials cannot be
forwarded to a different endpoint.

## Scheduling and Recovery

- A local-time alarm is recalculated each day, not a repeating 24-hour timer.
- The alarm enqueues network-constrained WorkManager work. Upload happens at
  or after 1 AM; offline devices, Doze, OS throttling, and manufacturer battery
  restrictions can delay execution. Exact alarm access does not guarantee an
  HTTP request completes at precisely 1 AM.
- The next unfinished date is persisted only after a successful response.
  Missed days since enabling are drained oldest-first, up to seven per worker
  attempt, then continued through a retry. This is not a full historical dump.
- Before 1 AM, yesterday is not yet due for an automatic backup. Manual uploads
  can run at any time. First enabling starts with the day due at the next 1 AM;
  use the manual button to upload yesterday or another selected date immediately.
- Reboot, app updates, clock/time-zone changes, and granting exact-alarm access
  restore scheduling. Reopening the app also restores alarms and drains due days.
- Android force-stop blocks alarms/work until the app is opened again. Revoking
  exact-alarm permission can remove scheduled alarms; reopen the app afterward.
  Some phones require allowing background activity in battery settings.
- Local calendar dates follow the device time zone at export time. Changing
  zones across days can produce overlapping or omitted UTC intervals; keep a
  stable device zone if you require strictly continuous UTC coverage.

## Privacy and Distribution

SMS includes private conversations and one-time codes. Only back up devices
you are authorized to access, to a server you control or trust. Use server-side
authentication, access controls, retention limits, and encrypted storage.

Bearer tokens are encrypted with an Android Keystore AES-GCM key. App backup
and device-transfer extraction are disabled. JSON files are generated in private app
cache and deleted after each attempt; a process crash can leave a cache file
until Android clears it or app data is cleared. Bodies and credentials are not
logged. Uploaded messages retain their exact text after JSON decoding. Avoid
logging message bodies or credentials on the server.

`READ_SMS` is a restricted Android permission. An installer may need to allowlist
it even for sideloaded builds. Google Play restricts SMS permissions; a backup
app needs an eligible approved exception and the corresponding declaration.
This project does not bypass permission restrictions or make itself the default
SMS handler. Test installation through your intended distribution channel.

## Verification

Run the dependency-free JVM tests with any JDK 17+:

```sh
./scripts/test-core.sh
```

The core tests cover DST calendar boundaries, exact 1 AM transitions, the
automatic due-day cutoff, manual date validation and defaults, and HTTP endpoint
policy. JSON unit tests round-trip multiline bodies, quotes, backslashes,
Unicode, control characters, empty/null fields, typed values, and empty days.
For JSON tests, Android build, and lint checks:

```sh
./gradlew testDebugUnitTest assembleDebug lintDebug
```

On a device, test permission denial/revocation, manual upload, an empty inbox,
multiline/non-ASCII messages, 401/429/500 responses, offline recovery, duplicate
idempotency keys, reboot, time-zone changes, exact-alarm denial, and disabling
while queued. Use a disposable device/test inbox rather than real SMS for tests.