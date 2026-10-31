# BoundaryLab Kotlin SDK

Minimum Android API 30 is required for the public `Os.fcntlInt` primitive.
Runtime support claims apply only to targets recorded by QA.

Consume `dev.boundarylab:boundarylab-android:0.1.0-SNAPSHOT` as an AAR from a
locally published Maven folder. The matching core JAR resolves transitively.
These development coordinates are not a Maven Central release. See the
[library guide](../../docs/LIBRARY-GUIDE.md) for publication, repository setup,
and an independent consumer; the library needs a separately installed broker.

```kotlin
dependencies {
    implementation("dev.boundarylab:boundarylab-android:0.1.0-SNAPSHOT")
}
```

Build with JDK 17 and Android SDK 36; the minimum installed Android API is 30.
Android library `:sdk:kotlin`, package `dev.boundarylab.sdk`. It exposes the shared
`dev.boundarylab.core.FieldPackCodec` through an API dependency. The sole AIDL
interface is `dev.boundarylab.ipc.IBoundaryBroker`; applications normally use
`BoundaryClient` instead. See the [architecture decisions](../../docs/decisions/BUILD-DECISIONS.md).

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
```

When foreground demand ends (for example, activity stop or engine destruction),
call `client.close()`. Keep the client alive while its callbacks are in use.

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

SDK and broker verify each other's signing identity. `BoundaryClient(context)`
retains the default endpoint. To use an adopter-owned broker, pass its exact
component and permission:

```kotlin
val endpoint = BrokerEndpoint(
    ComponentName("com.example.boundarybroker", "dev.boundarylab.broker.BoundaryBrokerService"),
    "com.example.boundarybroker.permission.BIND_BROKER"
)
val client = BoundaryClient(applicationContext, endpoint)
```

Import `android.content.ComponentName`, `dev.boundarylab.sdk.BrokerEndpoint` and
`dev.boundarylab.sdk.BoundaryClient`. Add both declarations to the **consumer
application** manifest; passing an endpoint cannot create Android permissions
or package visibility:

```xml
<uses-permission android:name="com.example.boundarybroker.permission.BIND_BROKER" />
<queries><package android:name="com.example.boundarybroker" /></queries>
```

The service class is fully qualified and can differ from the APK application ID.
All endpoint names must be fully qualified; invalid names throw
`IllegalArgumentException`. See [broker configuration](../../android/broker/README.md)
for the matching APK build. The SDK's default permission/query entries remain for
compatibility; custom declarations are additive.

Before binding and again before handshake, the SDK verifies the exact enabled,
exported component, exact required permission, permission ownership by the broker
package, signature-level protection and matching client/broker signatures. A
missing or hidden service/permission and invalid identity return `DENIED` before
session tokens or descriptors are sent. An unavailable bind after identity checks
can still return `UNAVAILABLE`. Configuration selects an address; it does not
grant an independently signed app access. The broker captures each actual calling
UID and authorizes its session. Sign approved broker and consumer APKs with the
same private development or product certificate; keep keys outside the repository.

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
