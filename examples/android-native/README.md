# Native FieldPack client

The `dev.boundarylab.nativeclient` Android application uses the separate
`dev.boundarylab.broker` application through `:sdk:kotlin`. It never hashes
attachments locally. Install both debug APKs built with the same development
signing certificate. No signing key is stored in this module.

Select 1–32 stable documents through Android's system document picker. The app
opens each read-only on one IO lane, sends its descriptor to the broker, and
waits for that file's successful terminal result before proceeding. The broker
checks supported whole-file properties. Pipe/cloud-provider behavior is not
silently treated as ordinary local file support.

Public aliases are deterministic (`attachment-001.bin`, etc.). The local display
name mapping appears after each provider open. Transfer originals separately
under these alias filenames. The manifest contains aliases, lengths and digests,
not source URIs, private paths or original names. It compares bytes and does not
authenticate a sender or promise a snapshot of changing files.

Every explicit preparation starts a new attempt and rehashes every selection.
Failure, cancellation, connection loss, changed selection and Activity disposal
invalidate export. Cancellation remains pending until the open/job completes;
blocked providers have no invented hard deadline. Session history holds at most
ten text summaries in memory and never enables an old export.

Export uses `ACTION_CREATE_DOCUMENT` after full manifest validation through the
shared codec. The same eligible attempt must still exist when the destination
picker returns. A successful write, flush and close precede “Manifest exported”.
A failed provider may leave an incomplete destination; the UI reports this and
retains the complete prepared bytes for an explicit retry. Generic SAF providers
do not promise atomic publication or crash durability. No persisted grants or
all-files storage permission are requested.

The optional Generated workload section submits a controlled 65,537-byte
seed-zero sample through Binder. It is separate from attachment results.

## Launch and measurement boundary

Default launch is the existing no-work baseline: `MainActivity` constructs
`PackController` and the SDK client, but neither calls `connect` until an explicit
Prepare pack or Run generated sample action. The SDK constructor does not bind,
open a broker session or submit work. Its executor starts no task at construction.
This source property still needs a device observation before claiming measured
no-bind behavior. There is no meaningful startup IPC delta to invent.

Measure explicit connect-to-ready and submit-to-terminal/visible intervals,
separating cold/warm broker state and storage reads from generated compute.
An identical in-process generated interaction baseline is not implemented here;
its absence must remain explicit in any comparison. No physical latency or
memory result is implied by a successful build or emulator run.

## Checks

From the repository root with the pinned toolchain:

```sh
./gradlew :examples:android-native:testDebugUnitTest :examples:android-native:lintDebug
./gradlew :examples:android-native:assembleDebug :examples:android-native:assembleDebugAndroidTest
./gradlew :examples:android-native:connectedDebugAndroidTest
```

`FieldPackFlowTest` is real cross-package SDK instrumentation: distinct broker
PID, independent generated oracle, actual file descriptors for `abc`/empty,
second-file failure, fresh-attempt rehash, cancellation gating, a delayed-provider
disposal regression and same-UID/different-session SDK handle rejection.
`BrokerWireTest` bypasses SDK normalization to test actual Binder declared-length
rejection and retained-request dedup/conflict. The debug-only, signature-protected
`OwnerFixtureService` lets the broker test package verify cross-UID denial against
a native-owned session. It and the delayed fixture provider are absent from the
release application; the provider lives only in the instrumentation APK. The tests require an already installed same-signed broker.
They do not themselves prove picker taps/export destination behavior, differently
signed denial, broker kill/rebind, hostile-provider races or physical performance.
Those require separately recorded device/provider evidence. Compilation and
unit-test success alone are not an installed journey.

The additional `brokerDeathRequiresFreshEpochAndExplicitNewSubmission` device
case force-stops only the owned broker package, checks loss of the old handle,
then requires a new epoch and an explicit new generated submission. Run this
suite serially with other broker consumers. Its first execution exposed an SDK
error-classification bug; consult [current receipts](EVIDENCE.md) for the corrected
SDK's rerun status rather than treating compilation as a passing recovery test.
