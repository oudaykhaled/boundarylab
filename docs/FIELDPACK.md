# FieldPack v1 normative contract

Status: implementation contract. Native Android, Flutter, JVM and conditional AOSP capabilities remain planned until their execution evidence exists. The Python standard-library reference is a separate implementation of manifest creation and local verification. This document governs the reference and future file adapters; it does not claim that the native integrations are implemented.

## Purpose and limits

FieldPack records SHA-256 checksums for explicitly selected local inspection attachments. A recipient supplies the manifest and a separate local directory containing files under the manifest aliases. Verification detects differences from recorded lengths/digests. There is no upload, automatic transfer, cross-device synchronization or durable middleware execution.

A digest provides integrity comparison, not authenticity, authorship, malware detection or a digital signature. Neither a successful file read nor a complete manifest proves an atomic snapshot of concurrently mutable input. The supported workflow uses stable, user-authorized files. Metadata checks detect some modifications, not all adversarial or same-size changes.

The terms MUST, MUST NOT and SHOULD describe project requirements. Unsupported input MUST fail explicitly rather than silently changing these semantics.

## Manifest format

A manifest MUST be UTF-8 JSON without a BOM, at most 16,384 encoded bytes. Its top-level keys MUST be exactly `format`, `version`, `algorithm`, `files`:

- `format`: the string `fieldpack`.
- `version`: the integer `1`; a JSON boolean is not an integer for this contract.
- `algorithm`: the string `sha256`.
- `files`: a nonempty array containing at most 32 entries, sorted strictly ascending by ASCII `name`.

Each entry MUST have exactly `name`, `size`, `sha256`:

- `name`: a public flat alias meeting the rules below.
- `size`: an integer from 0 through 134,217,728 inclusive, excluding booleans.
- `sha256`: exactly 64 lowercase hexadecimal characters.

The sum of entry sizes MUST NOT exceed 134,217,728 bytes. Duplicate object keys, unknown fields, duplicate aliases, unsupported versions/algorithms, non-finite numeric values, wrong types and invalid encoding MUST be rejected. Validate the complete manifest before opening any attachment. An implementation MAY reject malformed/excessively nested JSON before schema validation. Input parsing MUST be bounded by the encoded-byte cap.

Writers MUST sort entries, use compact JSON with no unnecessary spaces, preserve top-level key order `format`, `version`, `algorithm`, `files` and entry order `name`, `size`, `sha256`, and append one LF. Readers MUST enforce entry sorting but MUST NOT depend on JSON object key order or whitespace. The manifest is not a signed/canonical-JSON envelope.

No original source paths, URIs, original filenames distinct from aliases, timestamps, device identifiers or owner identity belong in the manifest. Logs SHOULD use aliases rather than private source paths.

## Alias grammar

An alias MUST contain 1–64 ASCII bytes and match `[a-z0-9][a-z0-9._-]{0,63}`. It MUST NOT end with a dot. It MUST NOT be `.` or `..`. It MUST NOT contain slash, backslash, colon, whitespace, non-ASCII characters or percent escapes. No URL decoding or Unicode normalization is performed.

Reject a DOS-reserved basename before the first dot: `con`, `prn`, `aux`, `nul`, `com1` through `com9`, and `lpt1` through `lpt9`. Thus `con.txt` is invalid and `company.txt` is valid. These names and trailing-dot restrictions reduce cross-platform filename ambiguity; they are not a claim that Windows is an implemented target.

Aliases are filenames in the recipient directory. A mobile application may assign names such as `attachment-001.bin`; the separately transferred files must use the same names. Alias creation does not copy, rename or transfer source files implicitly.

## File selection and descriptor rules

The local reference operates on an explicit input root and explicit flat aliases. It MUST NOT recursively discover arbitrary user files. Output MUST be outside the resolved input root, and input data MUST remain unchanged by creation or verification.

For the POSIX reference, open the root as a directory and retain its descriptor throughout the operation. Open aliases relative to that descriptor with no-follow semantics; do not validate a string and then reopen by a different absolute path. Final-component symlinks MUST be rejected. Use nonblocking open where required to prevent a FIFO open from hanging before type validation. Inspect the opened descriptor, not only a prior path stat. Reject every input that is not a regular, seekable file, and reject descriptors whose `st_nlink != 1`. Directories, devices, sockets, pipes, symlinks and hard-linked files are unsupported even if readable. Platforms lacking the required descriptor-relative/no-follow primitives MUST report unsupported operation rather than claim equivalent protection.

Read from position zero in chunks no larger than 65,536 bytes. Do not mmap or load an entire input into memory. A zero-length regular file is valid. Enforce the per-file and aggregate caps before and during reads; checked accumulation MUST prevent overflow. Capture descriptor identity and size/mtime/ctime metadata before and after hashing, compare actual bytes consumed with the initial size, and reject observed changes. Revalidate the alias against the root after processing to detect observed replacement. Keep reading bound to the opened file even if its directory name changes. These checks remain best effort: they cannot guarantee that a concurrently modified file was observed at one instant.

For verification, a known length different from the manifest reports `SIZE_MISMATCH`; an otherwise matching length with a different digest reports `DIGEST_MISMATCH`. Detected mutation during the read, unsafe file type/link count, unreadability or inability to establish the required input properties reports `UNREADABLE_OR_UNSUPPORTED`. Do not publish a success manifest from a failed or interrupted creation.

## Reference operation results

Verification produces one result per manifest entry, in manifest order:

| Outcome | Meaning |
|---|---|
| `MATCH` | Supported file observed with recorded size and digest |
| `MISSING` | Alias does not exist in the supplied root |
| `SIZE_MISMATCH` | Existing supported file has a different size |
| `DIGEST_MISMATCH` | Size matches but computed digest differs |
| `UNREADABLE_OR_UNSUPPORTED` | File cannot be safely read under this contract, including observed mutation |

Extra files in the recipient directory are ignored. This is verification of the listed set, not proof that the directory contains no additional files.

Exit status 0 means a manifest was successfully created or all listed entries matched. Exit status 1 means a valid verification operation completed with one or more non-`MATCH` entry outcomes, including unreadable entries. Exit status 2 means invalid arguments, invalid manifest, inaccessible/unsupported root, failed creation, failed output publication or another operation-level error. A keyboard interrupt may use the conventional interrupt exit status; it MUST NOT leave a newly published partial manifest. If an operation cannot be completed, it MUST NOT report overall success because a subset matched.

## Atomic output publication

Creation MUST construct and validate the complete manifest before publication. The default MUST refuse to replace an existing output, including a symlink; no overwrite option is required for v1. Write a private temporary file in the output parent directory, flush and close it, then atomically publish without replacing an existing target. A check-then-rename sequence that can overwrite a racing target does not satisfy no-overwrite semantics. POSIX same-directory hard-link publication of the completed temporary file is one implementation option; the input-file hardlink restriction does not prohibit this output-publication technique.

On error or interruption, remove the temporary file where possible. Any published output MUST be a complete validated manifest. If interruption occurs after atomic publication, a complete output may remain; the implementation MUST NOT describe that as a partial output. No crash-durability or transactional source-file snapshot is promised. Validate the output parent/root relationship before publication; reject paths resolving inside the input root. Hostile concurrent replacement of directory ancestors outside pinned descriptors is outside the reference threat model and MUST NOT be described as fully prevented.

## Job protocol and baseline resource limits

The file workflow extends the existing volatile job protocol; it does not replace the generated-byte workload. Operation IDs are `generated-sha256-v1` and `regular-file-sha256-v1`. Providers advertise each capability explicitly. Protocol major/minor is 1/0; an unsupported major or absent capability fails before work admission.

| Resource | Exact baseline |
|---|---:|
| Single job/file bytes | 134,217,728 |
| Pack total bytes | 134,217,728 |
| Pack entries | 32 |
| Work buffer | 65,536 bytes |
| Workers | 1 |
| Queued jobs | 16 globally, plus at most one running |
| Sessions | 16 globally |
| Outstanding jobs per session | 4, including running |
| Descriptor metadata encoding | 4,096 bytes |
| Encoded IPC response/socket frame payload | 16,384 bytes |
| Retained terminal entries | 64 globally |
| Terminal TTL | 60 seconds after terminal commit |
| Accounted retained metadata/result bytes | 1,048,576 globally |
| Input descriptors | At most one per admitted file job; at most 17 broker-owned job inputs |
| Relative deadline | Integer 1–120,000 ms; default 30,000 ms |
| SDK status polling | At least 250 ms between polls per job; one outstanding poll per handle |

Metadata byte accounting excludes fixed runtime/object overhead, which requires independent memory measurement. Reject with explicit `BUSY` or `INVALID` before accepting work that violates caps. Evict the oldest retained terminal result first when retention capacity requires it. Expired/evicted IDs return `EXPIRED_OR_UNKNOWN`; bounded retention is not a durable receipt.

Request, session, job and epoch identifiers are canonical lowercase hyphenated UUIDs. The server creates session/job IDs and a new random epoch on each process incarnation. Owner identity comes from the transport, not client-provided package names. Jobs use monotonic deadline accounting. Round robin selects the next job among sessions with queued work; a running file is not preempted for fairness.

## State, cancellation and loss

One state owner serializes `ACCEPTED → RUNNING → SUCCEEDED/FAILED/CANCELLED`. Queued cancellation commits `CANCELLED`, removes the queued job and closes its input. Running cancellation records `cancelRequested`; its immediate response may remain `RUNNING(cancelRequested=true)`. The worker checks cancellation/deadline between bounded reads and requests a terminal commit. First terminal commit wins among completion, cancellation and deadline; a later event never overwrites it. Cancellation is cooperative and does not guarantee interruption of a blocked OS read. Do not terminate arbitrary threads to simulate a hard deadline.

Closing or losing a session revokes access to its handles and requests cleanup/cancellation. Descriptor release during running work may await cooperative cleanup. Broker process loss means `LOST_OR_UNKNOWN` for unresolved client jobs. Reconnection creates a new epoch; old handles fail `STALE_EPOCH`. There is no persistence, restored execution, automatic replay or exactly-once guarantee.

Deduplication is scoped to `(epoch, sessionId, requestId)` and retained records. For a file request, matching retained operation/declared metadata returns the original job and closes any newly submitted descriptor without reading it. Different metadata returns `CONFLICT`. An FD/path is not proof of identical bytes: changed input requires a fresh request ID. A submit timeout may mean admission occurred; query the original request while its session/epoch survives. Outside retention, deduplication is not promised.

A pack is assembled by the client from sequential successful file jobs. Export requires success for every entry in that explicit attempt. Failure, cancellation or loss prevents complete-pack success. A new attempt is user initiated and rehashes all selected inputs. Retaining an already obtained digest for display does not authorize silently combining old/new results into an atomic-snapshot claim.

## Platform boundaries

### Ordinary Android Binder

The user-facing client opens the selected URI read-only using its own authorization and submits a fresh `ParcelFileDescriptor`. The broker MUST NOT reopen a client URI or path under its own authority. Same-publisher signature permission plus captured caller UID/user and owner-bound session remain the trust model. A file descriptor delegates access to that opened object, not blanket filesystem access.

The broker validates read-only access, regular type, seekability, known size, link-count constraint and caps. Pipe-backed/nonseekable document providers are explicitly unsupported in v1. Binder unmarshalling creates a broker-side closeable handle; close it on rejection, duplicate submission, terminal cleanup or session cleanup. The client closes its own local handle after the synchronous IPC completes or fails. Positional reads from offset zero MUST avoid shared descriptor-offset assumptions. No document bytes are carried in large Binder parcels. Bounded ingress does not perform hashing or provider opening on Binder threads.

### JVM/macOS file profile

The host remains a separate JVM process over bounded Unix-domain socket frames. Standard Java Unix-domain sockets MUST NOT be assumed to transfer file descriptors. The host starts with an explicit authorized input root and accepts only aliases under it. File opening/ownership belongs to its adapter; the pure Kotlin core owns bounded processing and job policy.

The adapter MUST independently prove its no-follow, regular-file, link-count, seekability and root-boundary checks on the selected JDK/macOS filesystem. If required primitives are unavailable, fail the file capability gate or implement a separately reviewed bounded native/JNI helper; do not claim that path normalization is equivalent to descriptor-relative containment. JNI is not implicit in the baseline scope. The same-user trust boundary and any residual directory-replacement race MUST remain explicit. Generated-byte support may pass independently while file support remains unavailable.

### Conditional AOSP

The image-integrated native service exposes **only `generated-sha256-v1`** in this scope. It MUST advertise file capability as unsupported. Its purpose is native Binder/service-image/lifecycle/security evidence. User-file descriptors, arbitrary paths, URI opening, provider integration and FieldPack execution in the privileged service are excluded. Ordinary SDK apps do not bypass their broker through hidden platform APIs. A generated-byte AOSP vector is not evidence of FieldPack platform integration.

## Required vectors and negative cases

Known digest vectors:

| Bytes | Size | SHA-256 |
|---|---:|---|
| Empty | 0 | `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855` |
| ASCII `abc`, no newline | 3 | `ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad` |

A valid two-file manifest uses `abc.txt` then `empty.bin`. Creating and verifying actual files must pass, modifying `abc` to `abd` must produce `DIGEST_MISMATCH`, truncating it must produce `SIZE_MISMATCH`, and deleting it must produce `MISSING`.

Required negative tests include duplicate JSON keys, unknown fields, boolean size/version, unsupported version, empty list, unsorted/duplicate aliases, uppercase names, reserved names with extensions, trailing dot, traversal/separators, 33 entries, per-file/aggregate limit+1, manifest byte cap+1, symlink/hardlink/FIFO/directory inputs, unreadable file, observed growth/shrink/replacement, existing output, output inside input root, and interruption before publication. Boundary tests must demonstrate bounded reads without relying solely on process RSS. Native adapters additionally test shared descriptor cursor, duplicate submission descriptor closure, queued cancellation, both running completion/cancel orders, session death, epoch replacement and missing capability.

## Canonical architecture amendments

The prior architecture statement that real user files are out of scope is superseded for **bounded regular-file FieldPack inputs only**. Keep generated bytes as the controlled scheduling/IPC benchmark and report file/storage timing separately. Keep one worker, volatile sessions/jobs, bounded polling, same-publisher Android trust and a distinct desktop process. Do not add persistence, callbacks, batch atomicity, external-provider streaming or AOSP file processing by implication.

B01 owns manifest/job contracts and negative vectors; B02 owns bounded source abstraction; B03 owns descriptor transport; B04/B05 own native/Flutter file selection and pack assembly; B06 owns file/race/FD tests; B07 owns separately proven desktop file capability; B08 owns compiled capability evolution; B09 separates storage from IPC costs; B10 documents explicit manual transfer; B13/B14 remain generated-only platform scope. The Python example is useful executable reference evidence and MUST remain labeled separately from future native integration results.

## Primary references

[Android ContentResolver](https://developer.android.com/reference/android/content/ContentResolver), [ParcelFileDescriptor](https://developer.android.com/reference/android/os/ParcelFileDescriptor), [Android AIDL](https://developer.android.com/develop/background-work/services/aidl), [JDK FileChannel](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/channels/FileChannel.html), and [RFC 8259](https://www.rfc-editor.org/rfc/rfc8259) ground platform and JSON behavior. Numeric caps, alias restrictions, state decisions and supported-input policy are BoundaryLab contract choices, not platform guarantees.
