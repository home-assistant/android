# mTLS E2E fixtures

The `E2E` workflow runs `.maestro/onboarding-mtls.yaml` against Home Assistant behind a reverse
proxy that requires a client certificate on every request. Four pieces make that work:

- `generate-certs.sh <out-dir> [<san-list>]` creates a CA, a server certificate, and a client
  certificate plus its `.p12`, all valid one day, so nothing with a private key is committed. It
  also writes `<out-dir>/res`, the debug resources below. `E2E_MTLS_P12_PASSWORD` and
  `E2E_MTLS_CERT_ALIAS` set the `.p12` password and the name Android shows for the certificate.
- `nginx.conf.template` is the proxy, rendered by the official nginx image from
  `E2E_MTLS_UPSTREAM` (Home Assistant's `host:port`), with the certificates mounted at
  `/etc/nginx/certs`.
- `open-cert-installer.sh <serial> <client.p12>` opens the system certificate installer on the
  emulator; the Maestro flow types the password and the name.
- `network_security_config.xml` is the app's config plus a `debug-overrides` trust anchor for the
  generated CA. It only reaches a build made with `-PdebugExtraResDir=<out-dir>/res`.

## Running it locally

```bash
OUT=/tmp/mtls
# The host of the URL the flow gets below must be in this SAN list: 10.0.2.2 is the
# emulator's address for the machine running it.
.github/e2e/mtls/generate-certs.sh "$OUT" "DNS:homeassistant.internal,IP:10.0.2.2"

# Home Assistant on :8123 with the configuration in ../homeassistant. Under Docker Desktop
# the proxy's requests come from the bridge gateway, so add its range (e.g. 192.168.65.0/24)
# to trusted_proxies in your local copy. Then the proxy:
docker run -d --name e2e-mtls-proxy -p 8443:8443 \
  -e E2E_MTLS_UPSTREAM=host.docker.internal:8123 \
  -v "$OUT:/etc/nginx/certs:ro" \
  -v "$PWD/.github/e2e/mtls/nginx.conf.template:/etc/nginx/templates/default.conf.template:ro" \
  nginx:stable-alpine

./gradlew :app:assembleFullDebug -PnoStrictMode -PdebugExtraResDir="$OUT/res"
adb install -r app/build/outputs/apk/full/debug/app-full-debug.apk
# Opens the system certificate installer on the file; the flow drives it from there.
.github/e2e/mtls/open-cert-installer.sh emulator-5554 "$OUT/client.p12"

# The mTLS flow takes the proxy URL in the same HOME_ASSISTANT_URL variable as the plain flow.
maestro test \
  -e HOME_ASSISTANT_URL=https://10.0.2.2:8443 \
  -e HOME_ASSISTANT_USERNAME=citest -e HOME_ASSISTANT_PASSWORD='h7jk99&U' \
  -e MTLS_P12_PASSWORD=e2ep12pass -e MTLS_CERT_ALIAS=e2eclient \
  .maestro/onboarding-mtls.yaml
```
