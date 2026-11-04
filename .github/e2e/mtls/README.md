# mTLS end-to-end test

The `E2E` workflow runs `.maestro/onboarding-mtls.yaml`: a full onboarding against Home Assistant
behind a reverse proxy that requires a client certificate on every request. It covers the two
places the app presents that certificate: the onboarding WebView, through the system certificate
chooser, and OkHttp, on the registration request and the WebSocket.

## How it works

1. `generate-certs.sh` creates a CA, a server certificate and a client certificate for this run.
   They are valid one day and nothing with a private key is committed.
2. The debug APK is built with `-PdebugExtraResDir=<out-dir>/res`, which replaces its network
   security config with `network_security_config.xml` from this directory: the same config plus a
   `debug-overrides` trust anchor for the run's CA. A build without the property is unchanged.
3. nginx starts on port 8443 in front of Home Assistant with `ssl_verify_client on`, so even the
   first request fails without the client certificate.
4. `open-cert-installer.sh` opens the system certificate installer on the client `.p12`. The flow
   types the password and the certificate name, then onboards through the proxy, picking the
   certificate in the chooser when the WebView asks for it.

## Files

| File | Role |
| --- | --- |
| `generate-certs.sh <out-dir> [<san-list>]` | Writes `ca.pem`, `server.pem`, `client.pem`, their keys, `client.p12` and the `res/` directory for the build. The SAN list defaults to `DNS:homeassistant.internal`. |
| `nginx.conf.template` | The proxy. Rendered by the nginx image from `E2E_MTLS_UPSTREAM` (Home Assistant's `host:port`), with the certificates mounted at `/etc/nginx/certs`. |
| `network_security_config.xml` | The app's config plus the trust anchor, copied into `res/xml` by the script. |
| `open-cert-installer.sh <serial> <client.p12>` | Pushes the `.p12`, registers it with MediaStore and opens the installer on it. |

Environment the script and the flow read:

| Variable | Default | Used by |
| --- | --- | --- |
| `E2E_MTLS_P12_PASSWORD` | `e2ep12pass` | `generate-certs.sh`, and the flow as `MTLS_P12_PASSWORD` |
| `E2E_MTLS_CERT_ALIAS` | `e2eclient` | `generate-certs.sh` (friendly name), and the flow as `MTLS_CERT_ALIAS` |
| `HOME_ASSISTANT_URL` | | The proxy URL, same variable as the plain flow |

## Running it locally

Needs Home Assistant on port 8123 with the configuration in `../homeassistant`, Docker, and a
booted emulator. `10.0.2.2` is the emulator's address for the machine running it.

Generate the certificates. The host of the URL given to the flow must be in the SAN list:

```bash
OUT=/tmp/mtls
.github/e2e/mtls/generate-certs.sh "$OUT" "DNS:homeassistant.internal,IP:10.0.2.2"
```

Start the proxy:

```bash
docker run -d --name e2e-mtls-proxy -p 8443:8443 \
  -e E2E_MTLS_UPSTREAM=host.docker.internal:8123 \
  -v "$OUT:/etc/nginx/certs:ro" \
  -v "$PWD/.github/e2e/mtls/nginx.conf.template:/etc/nginx/templates/default.conf.template:ro" \
  nginx:stable-alpine
```

Build and install the app with the trust anchor, then open the certificate installer:

```bash
./gradlew :app:assembleFullDebug -PnoStrictMode -PdebugExtraResDir="$OUT/res"
adb install -r app/build/outputs/apk/full/debug/app-full-debug.apk
.github/e2e/mtls/open-cert-installer.sh emulator-5554 "$OUT/client.p12"
```

Run the flow:

```bash
maestro test \
  -e HOME_ASSISTANT_URL=https://10.0.2.2:8443 \
  -e HOME_ASSISTANT_USERNAME=citest -e HOME_ASSISTANT_PASSWORD='h7jk99&U' \
  -e MTLS_P12_PASSWORD=e2ep12pass -e MTLS_CERT_ALIAS=e2eclient \
  .maestro/onboarding-mtls.yaml
```

To check the proxy on its own: a request without the client certificate must get a 400 from nginx,
one with it reaches Home Assistant.

```bash
curl -sk https://localhost:8443/api/
curl -sk --cert "$OUT/client.pem" --key "$OUT/client.key" https://localhost:8443/api/
```
