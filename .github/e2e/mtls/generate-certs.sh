#!/usr/bin/env bash
# Generates the certificates the mTLS E2E flow needs, all valid for one day:
#
#   ca.pem / ca.key         test CA, issuer of the two certificates below
#   server.pem / server.key served by the nginx proxy, SAN from $2
#   client.pem / client.key the client certificate the app must present
#   client.p12              the same client certificate, installed on the emulator
#   res/xml, res/raw        debug resources that make the app trust ca.pem,
#                           picked up with -PdebugExtraResDir=<out-dir>/res
#
# Nothing here is committed: the workflow regenerates everything on each run.
#
# Usage: generate-certs.sh <out-dir> [<san-list>]
#   san-list defaults to DNS:homeassistant.internal; a local run against an
#   emulator adds IP:10.0.2.2.
#
# Environment:
#   E2E_MTLS_P12_PASSWORD  password of client.p12 (default e2ep12pass)
#   E2E_MTLS_CERT_ALIAS    friendly name of the p12 entry, shown by Android
#                          as the certificate name (default e2eclient)
set -euo pipefail

OUT_DIR="${1:?usage: generate-certs.sh <out-dir> [<san-list>]}"
SAN_LIST="${2:-DNS:homeassistant.internal}"
P12_PASSWORD="${E2E_MTLS_P12_PASSWORD:-e2ep12pass}"
CERT_ALIAS="${E2E_MTLS_CERT_ALIAS:-e2eclient}"
VALIDITY_DAYS=1
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

mkdir -p "$OUT_DIR/res/xml" "$OUT_DIR/res/raw"
cd "$OUT_DIR"

openssl req -x509 -newkey rsa:2048 -nodes -days "$VALIDITY_DAYS" \
  -subj "/CN=HA Android E2E CA" \
  -addext "basicConstraints=critical,CA:TRUE" \
  -keyout ca.key -out ca.pem

openssl req -newkey rsa:2048 -nodes \
  -subj "/CN=homeassistant.internal" \
  -addext "subjectAltName=$SAN_LIST" \
  -keyout server.key -out server.csr
openssl x509 -req -in server.csr -CA ca.pem -CAkey ca.key -CAcreateserial \
  -days "$VALIDITY_DAYS" -copy_extensions copy -out server.pem

openssl req -newkey rsa:2048 -nodes \
  -subj "/CN=$CERT_ALIAS" \
  -addext "extendedKeyUsage=clientAuth" \
  -keyout client.key -out client.csr
openssl x509 -req -in client.csr -CA ca.pem -CAkey ca.key -CAcreateserial \
  -days "$VALIDITY_DAYS" -copy_extensions copy -out client.pem

# SHA1/3DES rather than the PBES2/AES default: the Android certificate
# installer parses it on every API level the E2E workflow runs on.
openssl pkcs12 -export \
  -keypbe PBE-SHA1-3DES -certpbe PBE-SHA1-3DES -macalg sha1 \
  -inkey client.key -in client.pem -name "$CERT_ALIAS" \
  -passout "pass:$P12_PASSWORD" -out client.p12

rm -f server.csr client.csr ca.srl

cp "$SCRIPT_DIR/network_security_config.xml" res/xml/network_security_config.xml
cp ca.pem res/raw/e2e_ca.pem

echo "mTLS E2E certificates written to $OUT_DIR (valid $VALIDITY_DAYS day, SAN $SAN_LIST)"
