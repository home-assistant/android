#!/usr/bin/env bash
# Opens the system certificate installer on a client .p12 so a Maestro flow can
# finish the install (onboarding-mtls.yaml types the password and the name).
#
# The file goes through MediaStore: adb's shell user may grant a media URI to the
# installer, while a documents URI is refused and file:// paths under Download or
# the installer's own external directory fail with EACCES under scoped storage.
#
# Usage: open-cert-installer.sh <adb-serial> <client.p12> <client_legacy.p12>
set -euo pipefail

USAGE="usage: open-cert-installer.sh <adb-serial> <client.p12> <client_legacy.p12>"
SERIAL="${1:?$USAGE}"
P12="${2:?$USAGE}"
P12_LEGACY="${3:?$USAGE}"
ADB="${ADB:-adb}"
DEVICE_PATH=/storage/emulated/0/Download/client.p12
# Up to this API level the installer cannot read OpenSSL 3's default PKCS12
# algorithms and needs the legacy container.
LEGACY_P12_MAX_API=32

API_LEVEL=$($ADB -s "$SERIAL" shell getprop ro.build.version.sdk | tr -d '\r')
if ! [[ "$API_LEVEL" =~ ^[0-9]+$ ]]; then
  echo "Could not read the API level of $SERIAL (got '$API_LEVEL')" >&2
  exit 1
fi
if [ "$API_LEVEL" -le "$LEGACY_P12_MAX_API" ]; then
  P12="$P12_LEGACY"
fi

$ADB -s "$SERIAL" shell am force-stop com.android.certinstaller
$ADB -s "$SERIAL" push "$P12" "$DEVICE_PATH" >/dev/null 2>&1

# Android 11+ takes the path as the call argument; Android 10 expects it as a Uri
# extra the content command cannot pass and throws, so fall back to the scan
# broadcast it still honours. Both scans are asynchronous, hence the polling.
$ADB -s "$SERIAL" shell content call --uri content://media/ --method scan_file --arg "$DEVICE_PATH" >/dev/null 2>&1 || true
MEDIA_ID=""
for ATTEMPT in $(seq 1 20); do
  MEDIA_ID=$($ADB -s "$SERIAL" shell content query --uri content://media/external/downloads \
    --projection _id --where "\"_display_name='client.p12'\"" 2>/dev/null | sed -n 's/.*_id=\([0-9]*\).*/\1/p' | head -1)
  [ -n "$MEDIA_ID" ] && break
  if [ "$ATTEMPT" -eq 1 ]; then
    $ADB -s "$SERIAL" shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d "file://$DEVICE_PATH" >/dev/null 2>&1 || true
  fi
  sleep 1
done
if [ -z "$MEDIA_ID" ]; then
  echo "client.p12 did not show up in MediaStore on $SERIAL" >&2
  exit 1
fi
$ADB -s "$SERIAL" shell am start -a android.intent.action.VIEW -t application/x-pkcs12 \
  -d "content://media/external/downloads/$MEDIA_ID" --grant-read-uri-permission >/dev/null
echo "Certificate installer opened on $SERIAL"
