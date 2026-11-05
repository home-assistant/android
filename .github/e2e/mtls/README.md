# mTLS end-to-end test

Files for the mTLS part of the `E2E` workflow: an nginx reverse proxy that requires a client
certificate in front of the E2E Home Assistant, and the certificates generated for each run.
How the test works, how to run it locally and how to debug a failure is documented in the
[contributor documentation](https://contributors.home-assistant.io/companion/android/e2e-testing/).

| File | Role |
| --- | --- |
| `generate-certs.sh <out-dir> [<san-list>]` | Writes the CA, server and client certificates, `client.p12` and the `res/` directory for the build. |
| `network_security_config.xml` | The app's config plus a `debug-overrides` trust anchor for the run's CA, copied into `res/xml` by the script and built in with `-PdebugExtraResDir`. |
| `nginx.conf.template` | The proxy, rendered by the nginx image from `E2E_MTLS_UPSTREAM`. |
| `open-cert-installer.sh <serial> <client.p12>` | Opens the system certificate installer on the `.p12` through MediaStore. |
