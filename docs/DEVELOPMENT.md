# Development

## Build and verify

```bash
./gradlew :android:testDebugUnitTest :android:assembleDebug :android:lintDebug
```

## Run in an emulator

Start an existing Android emulator, then:

```bash
adb install -r android/build/outputs/apk/debug/android-debug.apk
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 1
adb shell am start -n dev.hacompanion.panel/.MainActivity
```

## Home Assistant connection

The normal flow is **Pair panel with Home Assistant** in administrator
controls. Discovery plus the six-digit approval code provisions an encrypted
panel credential; no Home Assistant user token is entered on the panel.

## Legacy development connection

Use **Configure HA** in the top-right corner and enter:

- A reachable local Home Assistant URL, including port when needed.
- A temporary long-lived access token created from the Home Assistant user profile.

The access token is encrypted with an Android Keystore AES-GCM key. It is never
included in diagnostics or application logs.

Manual token entry is a pre-pairing debug fallback. Successful pairing clears
it and switches state, service, layout, and doorbell traffic to the scoped
panel connection.

For a debug APK, settings can instead be injected over ADB so no typing is
required on the panel:

```bash
adb -s PANEL_ADDRESS shell am start \
  -n dev.hacompanion.panel/.MainActivity \
  --es dev.hacompanion.panel.HA_URL http://homeassistant.local:8123 \
  --es dev.hacompanion.panel.HA_TOKEN YOUR_TEMPORARY_TOKEN
```

This path is disabled in release builds. The activity removes the extras after
provisioning and stores the token encrypted with Android Keystore.

The current client:

- Converts HTTP/HTTPS URLs to the HA `/api/websocket` endpoint.
- Follows the HA `auth_required` → `auth` → `auth_ok` handshake.
- Subscribes to `state_changed` events after authentication.
- Uses WebSocket ping frames.
- Retries connection failures with capped exponential backoff.
- Stops retrying after an explicit authentication failure.
