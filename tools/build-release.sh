#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd "$(dirname "$0")/.." && pwd)"
output_dir="$project_root/release-output"
fingerprint_file="$project_root/release-signing-certificate.sha256"

: "${NSPANEL_RELEASE_KEYSTORE:?Set NSPANEL_RELEASE_KEYSTORE to the permanent keystore path}"
: "${NSPANEL_RELEASE_KEY_ALIAS:?Set NSPANEL_RELEASE_KEY_ALIAS}"
: "${NSPANEL_RELEASE_STORE_PASSWORD:?Set NSPANEL_RELEASE_STORE_PASSWORD}"
: "${NSPANEL_RELEASE_KEY_PASSWORD:?Set NSPANEL_RELEASE_KEY_PASSWORD}"
: "${NSPANEL_VERSION_CODE:?Set NSPANEL_VERSION_CODE to an increasing integer}"
: "${NSPANEL_VERSION_NAME:?Set NSPANEL_VERSION_NAME, for example 1.0.0-beta.1}"

case "$NSPANEL_VERSION_CODE" in
  ''|*[!0-9]*) echo "NSPANEL_VERSION_CODE must be a positive integer" >&2; exit 2 ;;
esac
if [ "$NSPANEL_VERSION_CODE" -le 0 ]; then
  echo "NSPANEL_VERSION_CODE must be a positive integer" >&2
  exit 2
fi
if [ ! -f "$NSPANEL_RELEASE_KEYSTORE" ]; then
  echo "Release keystore not found: $NSPANEL_RELEASE_KEYSTORE" >&2
  exit 2
fi
if [ ! -f "$fingerprint_file" ]; then
  echo "Pinned release certificate fingerprint is missing: $fingerprint_file" >&2
  exit 2
fi
pinned_fingerprint="$(tr -d '[:space:]:' < "$fingerprint_file" | tr '[:upper:]' '[:lower:]')"
if [ -n "${NSPANEL_RELEASE_CERT_SHA256:-}" ] && [ "$NSPANEL_RELEASE_CERT_SHA256" != "$pinned_fingerprint" ]; then
  echo "Configured release certificate does not match the pinned fingerprint" >&2
  exit 2
fi

cd "$project_root"
./gradlew clean testDebugUnitTest assembleRelease

mkdir -p "$output_dir"
apk_name="nspanel-companion-${NSPANEL_VERSION_NAME}-arm64.apk"
cp android/build/outputs/apk/release/android-release.apk "$output_dir/$apk_name"

# The published metadata claims the pinned fingerprint, and both updaters refuse
# any APK that does not match it. Verify the signer that was actually produced
# before that claim is written, so a wrong or truncated keystore fails the build
# instead of shipping an unusable release.
signed_fingerprint="$(keytool -printcert -jarfile "$output_dir/$apk_name" \
  | awk -F': ' '/SHA256:/{gsub(":", "", $2); print tolower($2); exit}')"
if ! printf '%s' "$signed_fingerprint" | grep -Eq '^[0-9a-f]{64}$'; then
  echo "Unable to read the signing certificate of $apk_name" >&2
  exit 2
fi
if [ "$signed_fingerprint" != "$pinned_fingerprint" ]; then
  echo "Signed APK certificate does not match the pinned fingerprint" >&2
  echo "  pinned: $pinned_fingerprint" >&2
  echo "  signed: $signed_fingerprint" >&2
  exit 2
fi

python3 tools/write-release-metadata.py \
  --apk "$output_dir/$apk_name" \
  --version "$NSPANEL_VERSION_NAME" \
  --version-code "$NSPANEL_VERSION_CODE" \
  --certificate-sha256 "$pinned_fingerprint" \
  --output "$output_dir/release.json"

cd "$output_dir"
shasum -a 256 "$apk_name" release.json > SHA256SUMS
echo "Release artifacts created in $output_dir"
