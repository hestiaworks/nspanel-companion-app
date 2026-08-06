#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd "$(dirname "$0")/.." && pwd)"
signing_dir="${1:-$project_root/release-signing}"
keystore="$signing_dir/nspanel-companion-release.jks"
certificate="$signing_dir/nspanel-companion-release.pem"
credentials="$signing_dir/release-secrets.env"
fingerprint_file="$signing_dir/certificate-sha256.txt"
alias_name="nspanel-companion"

if [ -e "$signing_dir" ]; then
  echo "Refusing to replace an existing signing directory: $signing_dir" >&2
  exit 2
fi

mkdir -m 700 "$signing_dir"
store_password="$(openssl rand -hex 24)"
key_password="$(openssl rand -hex 24)"

keytool -genkeypair \
  -keystore "$keystore" \
  -storetype JKS \
  -storepass "$store_password" \
  -keypass "$key_password" \
  -alias "$alias_name" \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000 \
  -dname "CN=NSPanel Companion, OU=Release, O=NSPanel Companion, L=Kyiv, C=UA" \
  -noprompt >/dev/null

keytool -exportcert -rfc \
  -keystore "$keystore" \
  -storepass "$store_password" \
  -alias "$alias_name" \
  -file "$certificate" >/dev/null

fingerprint="$(keytool -list -v -keystore "$keystore" -storepass "$store_password" -alias "$alias_name" \
  | awk -F': ' '/SHA256:/{gsub(":", "", $2); print tolower($2); exit}')"
if ! printf '%s' "$fingerprint" | grep -Eq '^[0-9a-f]{64}$'; then
  echo "Unable to read the generated certificate fingerprint" >&2
  exit 2
fi

keystore_base64="$(base64 < "$keystore" | tr -d '\n')"
umask 077
{
  printf "export NSPANEL_RELEASE_KEYSTORE=%q\n" "$keystore"
  printf "export NSPANEL_RELEASE_KEY_ALIAS=%q\n" "$alias_name"
  printf "export NSPANEL_RELEASE_STORE_PASSWORD=%q\n" "$store_password"
  printf "export NSPANEL_RELEASE_KEY_PASSWORD=%q\n" "$key_password"
  printf "export NSPANEL_RELEASE_CERT_SHA256=%q\n" "$fingerprint"
  printf "export NSPANEL_RELEASE_KEYSTORE_BASE64=%q\n" "$keystore_base64"
} > "$credentials"
printf '%s\n' "$fingerprint" > "$fingerprint_file"
chmod 600 "$keystore" "$certificate" "$credentials" "$fingerprint_file"

echo "Permanent release identity created in: $signing_dir"
echo "Certificate SHA-256: $fingerprint"
echo "Back up the entire directory to two secure locations before publishing."
echo "Load local build credentials with: source '$credentials'"
