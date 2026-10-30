# BoundaryLab protocol 1.0

Status: frozen implementation contract, 2026-10-30. Runtime evidence is separate.
The normative [FieldPack contract](../docs/FIELDPACK.md) and
[build decisions](../docs/decisions/BUILD-DECISIONS.md) govern this protocol.

## Authority and compatibility

Every session/job operation uses transport-derived owner, server session ID and epoch.
Android captures Binder calling UID before dispatch. The socket host creates owner identity
for the connection. A wire-supplied owner/package/PID never grants access. IDs are lowercase
hyphenated UUIDs. A new process creates a random epoch. Old epoch requests fail STALE_EPOCH;
foreign live sessions fail DENIED; revoked sessions fail EXPIRED_OR_UNKNOWN. Unknown or expired job/request lookup fails
EXPIRED_OR_UNKNOWN. Major versions other than 1 fail INCOMPATIBLE before admission.

`generated-sha256-v1` emits byte at offset i = low eight bits of signed 64-bit wrapping
`seed + i`. This is deterministic test data, not randomness. `regular-file-sha256-v1`
requires an adapter-validated positional source. File capability is advertised separately.

## Public implementation boundary

`dev.boundarylab.core.CoreEngine` provides capabilities, openSession, submit, status,
statusByRequest, cancel, closeSession and close. Canonical operations take owner,
sessionId and epoch explicitly. Models and limits live in `core/Models.kt`; the exact
method signatures are frozen in ADR-B02. `PositionalSource` supplies size, readAt,
verifyUnchanged and close. It transfers to the core at submit invocation, including
validation failure, admission rejection and duplicate paths. An adapter closes failures
before that transfer. File sources must affirm unchanged metadata after exact-byte reading.

The Android library `:sdk:kotlin` owns the only AIDL definition and typed callback SDK.
Callbacks return to main; Binder calls execute on its executor. Clients pass a local PFD
to submitFile; the SDK owns it immediately and closes it after synchronous IPC return or
failure. The received broker PFD is a different close owner. No URI/path or content bytes
are forwarded to the broker. `statusByRequest` resolves uncertain admission without replay.

Every admitted SDK call completes its callback exactly once. Disposal reports CLOSED;
generation loss reports LOST_OR_UNKNOWN. A five-second caller observation timeout reports
TIMEOUT_UNKNOWN without cancelling or replaying an unresolved Binder operation. Physical
descriptor cleanup can wait for that IPC to return. UI subscriptions independently guard
their active generation. Loss is delivered before recovery callbacks on the main queue.
Status and request-ID lookup share at least 250 ms spacing and one outstanding poll per
request; cleanup from an old generation cannot release a fresh generation's polling slot.

## Bounds

| Resource | Bound and rejection |
|---|---|
| Job length | 0–134217728 bytes; otherwise INVALID |
| Buffer | At most 65536 bytes |
| Worker / global queue | 1 running / 16 queued; BUSY |
| Sessions / outstanding per session | 16 / 4; BUSY |
| Descriptor metadata / response payload | 4096 / 16384 encoded bytes; INVALID |
| Terminal retention | 64 entries, 60000ms TTL, 1048576 encoded bytes; evict oldest |
| Relative deadline | 1–120000ms, default 30000; INVALID outside range |
| SDK polling | At least 250ms/job, one outstanding poll/handle |
| File descriptors | At most 17 admitted inputs; one/input job |

Integer validation precedes arithmetic. Byte budgets use subtraction or checked addition.
Retention accounts encoded request/result metadata, not JVM object overhead.

## State and race vectors

| Ordered events | Required outcome |
|---|---|
| queued → cancel → worker pick | CANCELLED; zero content reads; close once |
| running → request cancel → blocked read | RUNNING with cancelRequested; physical worker occupied |
| success commit → cancel | SUCCEEDED preserved |
| cancellation commit → success attempt | CANCELLED preserved |
| deadline commit → success attempt | FAILED/DEADLINE_EXCEEDED preserved |
| session close → status | EXPIRED_OR_UNKNOWN; running cleanup may continue |
| broker loss → reconnect → old handle | LOST_OR_UNKNOWN client observation; old epoch invalid |
| matching retained request → new descriptor | same job; close new descriptor; zero new reads |
| same request ID/different metadata | CONFLICT; close incoming source |
| retained terminal → TTL/eviction → status | EXPIRED_OR_UNKNOWN; lookup never restarts work |

Cancellation is cooperative between bounded reads. The worker remains occupied through
source cleanup. No replacement worker or hard interruption deadline is promised. The first
serialized terminal commit wins. Submit timeout is uncertain admission; it never requests
automatic resubmission. Dedup is scoped to epoch/session/request and retained records only.
After eviction a fresh explicit submission is new work; this is not exactly-once execution.

## FieldPack

`FieldPackCodec` is the shared JVM manifest codec. It parses at most 16384 UTF-8 bytes,
rejects duplicate JSON keys before map conversion, enforces exact integer types and schema,
safe lowercase flat aliases, strictly sorted unique entries, 32 files and 128 MiB aggregate.
Writers sort entries, emit compact prescribed key order and one LF. Every active pack attempt
requires all files to succeed before export; failure/cancel/loss invalidates the attempt.
Recipients obtain attachments separately with the exact aliases. Checksums compare bytes;
they do not authenticate senders or promise a consistent concurrent snapshot.

## Verification ownership

Core deterministic tests establish state/resource behavior and shared manifest syntax.
Android instrumentation must additionally prove cross-package identity, PFD passing,
shared cursor independence, link/type/access denial, 100-cycle FD recovery and process loss.
Desktop subprocess tests establish real socket boundaries and its separately reviewed
file adapter. Compilation and unit tests alone do not establish installed-client evidence.
