# ADR 0001: Native WebRTC session library for camera streaming and calls

- **Status:** Proposed
- **Date:** 2026-07-07
- **Scope:** Android companion app (design intentionally portable to iOS later)

## Context

Camera streaming in the companion app currently relies on two paths: ExoPlayer for HLS, and the
WebView (frontend dashboard cards) for WebRTC. The WebView path supports two-way audio via
`getUserMedia`, but this only works on **HTTPS origins**: Chromium enforces the secure-context
requirement for microphone capture inside the WebView, and there is no public API to relax it. A
large share of installations access Home Assistant over plain HTTP on the LAN
(`http://homeassistant.local:8123`), so two-way audio is simply unavailable to them today.

In addition, a doorbell "incoming call" experience (CallStyle notification, full-screen intent on
the lock screen, answer-before-unlock, pre-negotiated media) cannot be built on a WebView at all:
there is no WebView to host while the app is backgrounded, and media capture must run inside a
foreground service with `camera`/`microphone` service types on Android 14+.

## Decision

Build a **UI-free WebRTC session engine** based on libwebrtc, structured as dedicated Gradle
modules with published-quality boundaries, with the companion app as its first consumer. Do
**not** implement WebRTC as a Media3/ExoPlayer `DataSource`, and do not keep relying on the
WebView for native camera surfaces.

### Why not an ExoPlayer DataSource

Media3's `DataSource` is a pull-based byte-stream abstraction: ExoPlayer reads bytes from a URI
and performs its own extraction, buffering, decoding and rendering. WebRTC is a stateful session:
SDP negotiation, ICE, DTLS-SRTP, adaptive jitter buffering and decoding all happen inside
libwebrtc, which emits *decoded frames* through `VideoSink`. There is no meaningful byte stream to
hand to ExoPlayer without destroying WebRTC's latency management. The two-way audio send path (mic
capture, encoding, echo cancellation, audio routing) has no representation in ExoPlayer's model at
all. RTSP exists as a Media3 module because RTSP is client-driven and pull-shaped; WebRTC is not.

### Why not keep the WebView

| Requirement | WebView | Native libwebrtc |
|---|---|---|
| Two-way audio over HTTP (LAN) | ❌ blocked by secure-context rule | ✅ app-level `RECORD_AUDIO` |
| Call notifications / lock-screen answer | ❌ needs foreground activity | ✅ foreground service + CallStyle |
| Pre-negotiation on push arrival | ❌ cold-start dashboard | ✅ start ICE immediately |
| Deterministic mic release | ⚠️ known lock bug (#6153) | ✅ owned lifecycle |
| Media encryption | ✅ DTLS-SRTP | ✅ DTLS-SRTP (mandatory regardless of signaling transport) |

Note: on plain-HTTP setups only the SDP/candidate exchange is cleartext — the same exposure as the
rest of the Home Assistant session on that connection. Media is always encrypted.

## Goals and non-goals

**Goals:** live view with sub-second latency; push-to-talk and open-mic two-way audio; doorbell
call flow (push → ring → answer); reuse across native surfaces (camera tiles, widgets,
notifications, Wear later); testable signaling independent of libwebrtc; HTTP and HTTPS parity.

**Non-goals:** replacing ExoPlayer for HLS/recorded playback; generic video-conferencing features
(rooms, SFU, simulcast); casting.

## libwebrtc distribution

We need raw libwebrtc bindings, not a vendor SDK — LiveKit/Stream/Daily client SDKs assume their
own servers, while our "server" is go2rtc behind the Home Assistant WebSocket API.

**Selected: `io.github.webrtc-sdk:android-prefixed`** (libwebrtc fork maintained by LiveKit +
Flutter-WebRTC). It tracks upstream Chromium milestones closely, is the same build powering
flutter-webrtc's very large deployment base, carries SDK-oriented embedding fixes, and the
`-prefixed` variant is shaded to `livekit.org.webrtc`, avoiding class collisions if any dependency
ever ships `org.webrtc`. Licensing is BSD (libwebrtc), compatible with the app's Apache-2.0.

**Alternatives considered:**

- `io.getstream:stream-webrtc-android`: also a maintained pre-compiled libwebrtc with optional
  Compose renderer helpers; historically lags upstream milestones slightly more than webrtc-sdk.
- Google's `org.webrtc:google-webrtc` Maven artifact: abandoned (last published 2018-era).
- Building libwebrtc ourselves: multi-day toolchain and a large CI burden; revisit only if we need
  custom patches.

Rule: all `livekit.org.webrtc` imports are isolated inside `:webrtc-core` so the artifact can be
swapped without touching consumers.

## Module structure

```
:webrtc-core          UI-free session engine (this has the only libwebrtc dependency)
  ├── SignalingClient        interface (transport-agnostic) + signaling DTOs
  ├── CameraPlayer           player abstraction shared with the HLS path later
  ├── TwoWayAudio            capability interface for talk-back
  ├── WebRtcSession          one PeerConnection = one session, pure-Kotlin state machine
  └── PeerConnectionController  thin facade over libwebrtc + real implementation

:webrtc-signaling-ha  SignalingClient implementation over the app's WebSocket layer (:common)

:common               gains the camera/webrtc/* WebSocket commands (internal transport detail)
```

Planned follow-ups (separate PRs): `:webrtc-compose` (renderer), dashboard/external-bus
integration with HLS fallback through `CameraPlayer`, two-way audio UI, and the call flow
(foreground service + CallStyle notification), which lives in `:app` because it needs FCM,
notification channels and Home Assistant registration, but is written against `:webrtc-core`
interfaces only.

Dependency directions: `:webrtc-core` depends only on libwebrtc; `:webrtc-signaling-ha` depends on
`:webrtc-core` and `:common`; `:app` will depend on both. Neither new module may import from
`:app`. Publishing the modules (Maven Central) is deferred until the API is stable across two app
releases and a second consumer commits to using it.

## Signaling protocol (Home Assistant WebSocket API)

Verified against Home Assistant Core (`homeassistant/components/camera/webrtc.py`), available
since core 2024.11:

```
IDLE
 ├─ optional: camera/capabilities {entity_id} → require "web_rtc" in
 │            frontend_stream_types, else route to HLS path immediately
 └─ start() → camera/webrtc/get_client_config {entity_id}
              → result {configuration: RTCConfiguration, dataChannel?}
NEGOTIATING
 ├─ createOffer(recvonly video+audio; sendrecv audio if two-way requested)
 ├─ send camera/webrtc/offer {id: MSG_ID, entity_id, offer}
 │        → immediate result (this is a SUBSCRIPTION on MSG_ID)
 ├─ ← event {type:"session", session_id}   (ULID) → store id
 ├─ ← event {type:"answer", answer}        → setRemoteDescription
 ├─ ← event {type:"candidate", candidate}  (any time) → addIceCandidate
 ├─ ← event {type:"error", code, message}  → Failed(code)
 └─ local onIceCandidate → camera/webrtc/candidate
        {entity_id, session_id, candidate: RTCIceCandidateInit dict
         (candidate, sdpMid, sdpMLineIndex, usernameFragment)}
CONNECTED   (PeerConnection state CONNECTED, first frame → Playing)
 ├─ DISCONNECTED → wait → restartIce() with backoff (capped attempts)
 └─ FAILED → tear down → surface Failed(cause) → UI may fall back to HLS
CLOSING
 └─ stop()/release() → send unsubscribe_events {subscription: MSG_ID}
    (there is NO dedicated close command — unsubscribing triggers
     close_webrtc_session server-side; a dropped socket does the same)
    → dispose tracks/sources/PeerConnection, restore audio
```

Error codes surfaced by core: `webrtc_offer_failed`, `webrtc_candidate_failed`,
`webrtc_get_client_config_failed` (the latter two also fire as command errors when the camera
lacks WebRTC support).

The state machine is pure Kotlin with an injected `SignalingClient` and a thin
`PeerConnectionController` facade, so it is unit-testable with mocked SDP/ICE callbacks and no
native code. All native disposal is idempotent and ordered (tracks → sources → PeerConnection →
factory references).

## Two-way audio design

Offer the audio transceiver as `sendrecv` with the mic track initially disabled (fast path for
enabling talk without renegotiation; go2rtc's backchannel picks the direction up automatically).
`RECORD_AUDIO` is requested by the app, not a web origin — this is what makes HTTP parity work.
Audio focus, `MODE_IN_COMMUNICATION` enter/restore and Bluetooth routing are owned by the library
(follow-up PR) so consumers cannot leak the microphone — the #6153 class of bugs becomes
structural instead of per-consumer discipline.

## Testing

Unit-test the signaling layer and session state machine against mocked WebSocket responses and a
fake `PeerConnectionController` (happy path, error events, candidate-before-answer ordering,
socket drop mid-negotiation, teardown on every failure path). Instrumented tests against a real
go2rtc instance with a synthetic RTSP source are planned once the renderer lands (connect time,
first-frame latency, mic acquire/release, audio mode restoration).

## Open questions

1. Whether to pre-warm a `PeerConnectionFactory` at app start (faster first frame vs. memory).
2. H.265 handling: hardware decode support matrix vs. go2rtc transcode fallback.
3. iOS: the same core design maps to WebRTC.framework; decide whether interfaces live in a shared
   KMP module now or later.
4. Session-close semantics rely on WebSocket subscription lifetime — decide how the call flow
   keeps the socket (or a dedicated one) alive across the ring/answer window in the foreground
   service.
