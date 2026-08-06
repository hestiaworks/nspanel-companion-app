# NSPanel Companion — panel application

Native Android dashboard for the Sonoff NSPanel Pro. It replaces the stock
launcher and renders Home Assistant entities with native views rather than a
WebView.

## What it does

- Runs as the Android Home application and survives reboots
- Stays useful when Home Assistant is briefly unavailable, using a cached layout
- Native controls for climate, weather, lights, fans, covers, switches and timers
- Low-latency doorbell view with incoming audio and push-to-talk
- Paired to Home Assistant with an expiring approval code; no user token is
  stored on the device

## Requirements

- Sonoff NSPanel Pro (ARM64, Android 8.1 / API 26+)
- The [Home Assistant integration](https://github.com/hestiaworks/nspanel-companion-integration)

## Installation

Signed ARM64 APKs are published under [Releases](https://github.com/hestiaworks/nspanel-companion-app/releases)
and installed over network ADB — most easily through the
[updater add-on](https://github.com/hestiaworks/addons). Every release is signed
with a pinned certificate; both updaters verify the APK signer before installing.

## Building

```sh
./gradlew :android:testDebugUnitTest :android:assembleDebug
```

Release builds require external signing credentials — see `tools/build-release.sh`.

## Status

Beta.
