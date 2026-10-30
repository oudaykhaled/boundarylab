# BoundaryLab Kotlin SDK

Minimum Android API 30 is required for the public `Os.fcntlInt` primitive.
Runtime support claims apply only to targets recorded by QA.

Android library `:sdk:kotlin`, package `dev.boundarylab.sdk`. It exposes the shared
`dev.boundarylab.core.FieldPackCodec` through an API dependency. The sole AIDL
interface is `dev.boundarylab.ipc.IBoundaryBroker`; applications normally use
`BoundaryClient` instead. See `docs/decisions/BUILD-DECISIONS.md` for the architecture.

```kotlin
val client = BoundaryClient(applicationContext)
client.onLoss = { code -> invalidateActiveAttempt(code) }
client.connect { connected ->
    connected.onSuccess {
        client.submitGenerated(seed = 0, totalBytes = 65536) { submitted ->
            submitted.onSuccess { handle -> client.status(handle) { snapshot -> /* render */ } }
        }
    }
}
// End foreground demand, such as activity stop or engine destruction:
client.close()
```

`connect`, `submitGenerated`, `submitFile`, `status`, `statusByRequest`, and `cancel`
use Kotlin `Result` callbacks on Android's main thread. Binder calls run on one
owned SDK worker. `JobSnapshot` provides `state`, `bytesProcessed`, `sha256`,
`error`, `cancelRequested`, and `isTerminal`. `BrokerCapabilities` includes the
fresh `epoch`, protocol version, operation set, byte cap, and `brokerPid` for
boundary evidence. Errors are `BoundaryException.code`, never source paths.

`submitFile(pfd, alias, requestId, deadlineMs, callback)` takes ownership of the
local PFD immediately, including when the client is closed or busy. Do not close,
reuse or submit that handle again. The SDK closes it after synchronous IPC returns
or fails; the broker owns a different unmarshalled handle. Derive the descriptor
with `ContentResolver.openFileDescriptor(uri, "r")` under the client’s authorization.
Only a whole-file descriptor is supported. Do not strip offset/length metadata
from an `AssetFileDescriptor`: raw PFD cannot prove those discarded subsection
bounds. Cloud/pipe-backed, writable, nonregular and multi-link sources are rejected.
No document bytes cross a Dart channel or a Binder byte array.

SDK and broker verify each other's signing identity. The SDK checks the explicit
`dev.boundarylab.broker/.BoundaryBrokerService` component, signature, exported flag,
and `dev.boundarylab.permission.BIND_BROKER` permission before transmitting tokens
or descriptors. The broker authorizes each captured calling UID/session. Install
all approved demonstration apps with the same private development certificate;
keep keys outside the repository. This is a same-publisher capability.

A client owns one session and a generation. Death/disconnection invalidate old
handles; callbacks from old work complete with `LOST_OR_UNKNOWN` rather than stale
success. `close()` completes pending observations as `CLOSED`. Reconnect attempts
are capped at three per loss sequence while demand exists; close stops demand.
No request is replayed. A fresh client/connection does not recover volatile work.

Calls report `TIMEOUT_UNKNOWN` after five seconds without claiming that remote
work stopped. Keep the original request UUID. `statusByRequest(requestId)` can
resolve ambiguous admission while the original session remains live; it returns
a `JobHandle`, which can then be queried. Do not convert an unresolved timeout
into an automatic retry. A blocked IPC retains its SDK worker and descriptor
until it actually returns. Admission to the SDK queue is bounded to 32 operations.

Status and request-ID lookup share per-request polling admission: at least 250 ms
between IPC starts, one outstanding poll per request, a bounded recent timestamp
cache, and `BUSY` on concurrent polling. The SDK performs single queries; consumers
stop scheduling polls after a terminal result, loss, or disposal. Job deadlines
are separate from this caller waiting timeout; cancellation is cooperative.

Compilation/lint: `./gradlew :sdk:kotlin:assembleDebug :sdk:kotlin:lintDebug`.
Actual cross-package native instrumentation lives in `examples/android-native`.
Broker instrumentation covers SDK close/submission descriptor ownership races.
Source and compilation alone do not prove process-loss or device acceptance.
