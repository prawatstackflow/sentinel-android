# Sentinel Android SDK

Native Android SDK for the **Sentinel** identity-verification flow. It hosts the
deployed web verification runtime in a hardened `WebView` and adds a native
layer for camera-permission forwarding, document file selection, and a typed
result callback — so the chat/widget/branding UI stays in sync with web
automatically.

See [`docs/mobile-sdk-contract.md`](https://github.com/finvasia/sentinel) in the
platform repo for the cross-platform contract this SDK mirrors.

## Install

Distributed via [JitPack](https://jitpack.io). Add the JitPack repository, then the dependency:

```kotlin
// settings.gradle.kts → dependencyResolutionManagement { repositories { ... } }
maven { url = uri("https://jitpack.io") }

// build.gradle.kts
dependencies {
    implementation("com.github.prawatstackflow:sentinel-android:0.1.0")
}
```

Minimum SDK: **24**.

## Usage

```kotlin
class CheckoutActivity : AppCompatActivity() {

    private val verify = registerForActivityResult(Sentinel.Contract()) { result ->
        when (result) {
            SentinelResult.Approved    -> goToSuccess()
            SentinelResult.Rejected    -> goToRejected()
            SentinelResult.UnderReview -> goToPending()
            SentinelResult.Cancelled   -> { /* user dismissed */ }
            is SentinelResult.Failed   -> showError(result.message)
        }
    }

    private fun startVerification(sessionId: String, sessionToken: String) {
        verify.launch(
            SentinelConfig(
                sessionId = sessionId,
                sessionToken = sessionToken,
                // hostedFlowBaseUrl defaults to the Finvasia test environment;
                // override per environment:
                // hostedFlowBaseUrl = "https://identity.yourco.com",
                theme = SentinelTheme.SYSTEM,
            ),
        )
    }
}
```

`sessionId` and `sessionToken` come from **your backend** calling
`POST /sessions` (via `@finvasia-identity/node`) — never minted on the device.

## Demo app

The `demo/` module is a runnable harness with two ways to start a flow:

- **Start Demo Session** — mints a fresh session for you (calls `POST /sessions`)
  and launches the SDK in one tap. Configure it by copying
  [`local.properties.example`](local.properties.example) → `local.properties`
  (git-ignored) and setting `DEMO_API_KEY` to a sandbox tenant API key.
  `DEMO_API_BASE_URL` / `DEMO_HOSTED_FLOW_BASE_URL` default to the local LAN stack;
  point them at `https://test-api-identity.finvasia.com` /
  `https://test-identity.finvasia.com` for the deployed sandbox. Until a key is
  set, the button is inert. *(Demo only — never ship an API key in a real app;
  keep it server-side.)*
- **Manual** — paste a `sessionId` + `sessionToken` and tap **Start verification**.

```bash
./gradlew :demo:installDebug   # build & install on a device/emulator
```

## Host-app requirements

- **Internet** and **Camera** permissions are declared by the SDK and merged
  into your manifest. The SDK requests the runtime `CAMERA` permission itself
  when liveness needs it.
- A reasonably current **Android System WebView** (camera in WebView via
  `getUserMedia`).

## What the SDK handles

- Loads `{hostedFlowBaseUrl}/verification/{sessionId}?token=…&theme=…&platform=native`.
- Forwards the WebView camera permission request to the OS.
- Returns a typed [`SentinelResult`]; maps a failed page load to `Failed`.
- Keeps navigation within the hosted origin (foreign CTA links open in the
  browser).

## Known follow-ups

- Direct camera-capture for `<input type=file capture>` document inputs (live
  liveness via `getUserMedia` already works).
- Optional host-proxied session-token refresh for long-lived sessions.
