# IPv6 and WebView DNS resolution

This document describes why the Home Assistant Companion for Android implements custom DNS
and WebView networking, how the pieces fit together, and how to debug or extend the
implementation.

**Minimum supported API level:** 23 (`androidSdk-min` in `gradle/libs.versions.toml`).

## Background

### The Android AAAA problem

On many Android devices, hostname resolution skips **AAAA (IPv6)** lookups unless the device
has an IPv6 default route (typically a `2000::/3` route). Hostnames that resolve **only** to
IPv6 (no A record) then fail with `ERR_NAME_NOT_RESOLVED` in WebView or connection errors in
OkHttp—even when the network is IPv6-capable and other clients resolve the name correctly.

`InetAddress.getAllByName()` and `Network.getAllByName()` exhibit this behaviour. It affects
both the app’s OkHttp stack and Chromium’s built-in resolver inside WebView.

### Why WebView is special

The Home Assistant frontend loads in a **WebView**. Chromium uses its **own DNS and TLS stack**.
It does **not** call the app’s OkHttp `Dns` implementation or `NetworkAwareDns`.

Therefore, fixing DNS only for OkHttp is not enough for the main UI. WebView traffic must be
routed through code that uses `NetworkAwareDns`, while keeping the **original hostname** for
TLS (SNI and certificate validation).

### Approaches that do not work

| Approach | Why it fails |
|----------|----------------|
| Only `NetworkAwareDns` on OkHttp | WebView never uses it |
| Rewriting URLs to IPv6 literals (`https://[2001:db8::1]:8123/`) | TLS/SNI and certificate validation expect the domain name, not the IP |
| `shouldInterceptRequest` alone | Only GET/HEAD; WebSockets and some subresources still use Chromium DNS |
| Reading CONNECT headers with `BufferedReader` then relaying TLS | Buffered bytes of the TLS ClientHello are lost → `ERR_FAILED` (-1) |

## Architecture overview

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         App network traffic                              │
├──────────────────────────────┬──────────────────────────────────────────┤
│ OkHttp (REST, WebSocket,     │ WebView (Home Assistant frontend)         │
│ connectivity checks, …)      │                                           │
│         │                    │         │                                 │
│         ▼                    │    ┌────┴────┐                            │
│  NetworkAwareDns             │    │ Primary │ Fallback (no PROXY_OVERRIDE)│
│         │                    │    ▼         ▼                            │
│         │                    │ WebViewConnectProxyManager                │
│         │                    │     → LocalConnectProxy ──► lookup()      │
│         │                    │     HostnameWebViewRequestProxy (GET/HEAD)│
└─────────┴────────────────────┴──────────────────────────────────────────┘
```

### Components

| Component | Module | Role |
|-----------|--------|------|
| [`NetworkAwareDns`](../common/src/main/kotlin/io/homeassistant/companion/android/common/data/network/NetworkAwareDns.kt) | `:common` | OkHttp `Dns`; explicit AAAA + A on API 29+ via `DnsResolver`; on API 23–28 via [`NetworkBoundDnsLookup`](../common/src/main/kotlin/io/homeassistant/companion/android/common/data/network/NetworkBoundDnsLookup.kt) |
| [`NetworkBoundDnsLookup`](../common/src/main/kotlin/io/homeassistant/companion/android/common/data/network/NetworkBoundDnsLookup.kt) | `:common` | Raw DNS queries on the active network’s DNS servers when `DnsResolver` is unavailable |
| [`LocalConnectProxy`](../common/src/main/kotlin/io/homeassistant/companion/android/common/data/network/LocalConnectProxy.kt) | `:common` | Localhost HTTP CONNECT proxy; resolves targets with `NetworkAwareDns` and connects via `Network.socketFactory` |
| [`WebViewConnectProxyManager`](../app/src/main/kotlin/io/homeassistant/companion/android/frontend/webview/WebViewConnectProxyManager.kt) | `:app` | Starts proxy and sets `ProxyController` override when `WebViewFeature.PROXY_OVERRIDE` is supported |
| [`HostnameWebViewRequestProxy`](../common/src/main/kotlin/io/homeassistant/companion/android/common/data/network/HostnameWebViewRequestProxy.kt) | `:common` | Fallback: proxies GET/HEAD for the configured hostname through OkHttp when CONNECT proxy is unavailable |
| [`HAWebViewClient`](../app/src/main/kotlin/io/homeassistant/companion/android/util/HAWebViewClient.kt) | `:app` | Uses request interception only when the CONNECT proxy is **not** active; maps errors using `logicalHostname` |
| [`toLogicalHostname()`](../common/src/main/kotlin/io/homeassistant/companion/android/common/data/network/WebViewHostnameExtensions.kt) | `:common` | Extracts hostname from URL for error matching and intercept fallback |

## Resolution strategy by API level

### OkHttp / `NetworkAwareDns.lookup()`

| API level | Mechanism |
|-----------|-----------|
| **29+ (Q)** | `DnsResolver.query()` for TYPE_AAAA, then TYPE_A; fallback to `Network.getAllByName()` |
| **23–28** | `NetworkBoundDnsLookup` (UDP DNS on link DNS servers); fallback to `Network.getAllByName()` |
| **No active network** | `Dns.SYSTEM.lookup()` |

DNS work must not block the main thread. On API 29+, `DnsResolver` delivers I/O events on the
main looper; lookups started on the UI thread are dispatched to a background worker first
(see `runOffMainThread` in `NetworkAwareDns`).

### WebView

| Condition | Mechanism | Coverage |
|-----------|-----------|----------|
| `WebViewFeature.isFeatureSupported(PROXY_OVERRIDE)` | `WebViewConnectProxyManager.ensureConfigured()` → all WebView traffic via `LocalConnectProxy` | HTTPS, WSS, subresources (full tunnel) |
| PROXY_OVERRIDE **not** supported (older WebView / some API 23 devices) | `HostnameWebViewRequestProxy` via `HAWebViewClient.shouldInterceptRequest` | GET and HEAD to the logical hostname only |

`PROXY_OVERRIDE` depends on the **installed System WebView version**, not only the app’s
`minSdk`. Always check with `WebViewFeature.isFeatureSupported()` at runtime.

## Load sequence

Before loading a URL in WebView, callers invoke:

```kotlin
webViewConnectProxyManager.ensureConfigured()
webView.loadUrl(url)  // URL unchanged — no IPv6 literal rewrite, no extra headers
```

Integration points:

- [`FrontendViewModel.handleUrlResult`](../app/src/main/kotlin/io/homeassistant/companion/android/frontend/FrontendViewModel.kt)
- [`ConnectionViewModel.buildAuthUrl`](../app/src/main/kotlin/io/homeassistant/companion/android/onboarding/connection/ConnectionViewModel.kt)
- [`WebViewActivity.loadUrl`](../app/src/main/kotlin/io/homeassistant/companion/android/webview/WebViewActivity.kt)

If `ensureConfigured()` returns `false`, a warning is logged and the OkHttp intercept fallback
applies where possible.

### `logicalHostname`

`FrontendViewState` and onboarding expose a **logical hostname** (from the configured server
URL). `HAWebViewClient` uses it to attribute main-frame errors when the failing request URL
does not exactly match the loaded URL (e.g. redirects). It is **not** used to rewrite URLs.

## LocalConnectProxy implementation notes

1. Parse CONNECT request line and headers byte-by-byte (`readConnectProxyAsciiLine`) — **do not**
   use `BufferedReader`, or TLS bytes after the header block will be lost.
2. Resolve hostname with `NetworkAwareDns.lookup()` and connect with `Network.socketFactory`.
3. Reply `HTTP/1.1 200 Connection Established`, then relay both directions on the **same**
   `InputStream` used for header parsing.
4. On failure, send `HTTP/1.1 502 Bad Gateway` and log at warning level (`LocalConnectProxy` tag).

## Debugging

Filter logcat:

```bash
adb logcat -s NetworkAwareDns LocalConnectProxy WebViewConnectProxy
```

**Healthy CONNECT proxy path:**

```
NetworkAwareDns: lookup(ha.example.com): resolved on active network -> …
LocalConnectProxy: Local CONNECT proxy listening on 127.0.0.1:…
WebViewConnectProxy: WebView proxy configured via 127.0.0.1:…
```

**Fallback path:**

```
WebViewConnectProxy: WebView PROXY_OVERRIDE is not supported on this device
ConnectionViewModel: WebView CONNECT proxy unavailable, using OkHttp request interception fallback
```

**Common errors (historical):**

| Symptom | Typical cause |
|---------|-----------------|
| `ERR_NAME_NOT_RESOLVED` (-2) | Chromium DNS; fix not applied or PROXY_OVERRIDE + fallback both inactive |
| `ERR_FAILED` (-1) | Broken CONNECT relay (e.g. buffered TLS bytes) or proxy connect failure |
| SSL / certificate shows IP in URL | IPv6 literal URL rewrite (removed — do not reintroduce) |
| `DNS query timed out` on main thread | DNS invoked on UI thread without worker dispatch (fixed in `NetworkAwareDns`) |

## Tests

| Test class | Covers |
|------------|--------|
| [`NetworkAwareDnsTest`](../common/src/test/kotlin/io/homeassistant/companion/android/common/data/network/NetworkAwareDnsTest.kt) | OkHttp integration, address selection |
| [`NetworkBoundDnsLookupTest`](../common/src/test/kotlin/io/homeassistant/companion/android/common/data/network/NetworkBoundDnsLookupTest.kt) | DNS packet build/parse |
| [`LocalConnectProxyTest`](../common/src/test/kotlin/io/homeassistant/companion/android/common/data/network/LocalConnectProxyTest.kt) | CONNECT target parsing, line reading |
| [`HostnameWebViewRequestProxyTest`](../common/src/test/kotlin/io/homeassistant/companion/android/common/data/network/HostnameWebViewRequestProxyTest.kt) | Intercept fallback guards |
| [`WebViewConnectProxyManagerTest`](../app/src/test/kotlin/io/homeassistant/companion/android/frontend/webview/WebViewConnectProxyManagerTest.kt) | PROXY_OVERRIDE feature detection |
| [`HAWebViewClientTest`](../app/src/test/kotlin/io/homeassistant/companion/android/util/HAWebViewClientTest.kt) | Error mapping with `logicalHostname` |

## Known limitations

- **API 23 + no PROXY_OVERRIDE:** WebSockets and non-GET/HEAD requests still use Chromium DNS;
  IPv6-only hosts may fail for real-time frontend features even when the initial page loads via
  intercept.
- **Dual-stack hosts:** Address preference in `LocalConnectProxy` prefers IPv6 when no IPv4 is
  present; behaviour for mixed stacks follows existing selection logic in `selectAddress`.
- **androidx.webkit 1.15+** declares library `minSdk` 24; the **app** remains at minSdk 23.
  Runtime feature checks gate PROXY_OVERRIDE usage.

## Maintenance guidelines

- Do **not** rewrite WebView URLs to IP literals for DNS workarounds.
- Do **not** remove `HostnameWebViewRequestProxy` without guaranteeing PROXY_OVERRIDE on all
  supported devices down to API 23.
- Keep CONNECT header parsing off buffered readers.
- When adding new WebView entry points, call `ensureConfigured()` before `loadUrl()`.
- Prefer extending `NetworkAwareDns` / `NetworkBoundDnsLookup` for DNS changes rather than
  ad-hoc lookups in UI code.

## Related issues

This work addresses connectivity to hostnames that resolve only via AAAA (IPv6-only DNS), e.g.
Home Assistant instances published as AAAA-only records on IPv6-capable networks where Android
would otherwise skip AAAA resolution.
