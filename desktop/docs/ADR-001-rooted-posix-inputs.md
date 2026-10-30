---
status: accepted
date: 2026-10-30
decision-makers: ["Astra chief architect (AI)"]
consulted: ["Desktop implementation orchestrator (AI)", "Independent security reviewer (AI)"]
---

# ADR-001: Pin desktop file inputs through a bounded POSIX bridge

## Context and problem

FieldPack requires descriptor-relative no-follow opening, same-descriptor regular/read-only/seekable/link-count checks and bounded positional reads. On the inspected macOS JDK 21.0.5, the actual directory stream was `sun.nio.fs.UnixDirectoryStream`, not `SecureDirectoryStream`; Java's `unix:nlink` attribute is path based. Public JDK file APIs therefore do not prove this contract. The JVM host and CLI must remain separate processes, with no invented FD transfer over Java Unix sockets.

## Decision drivers

- Never enable a capability whose descriptor checks cannot be established.
- Keep job policy, hashing, limits and cancellation in the shared Kotlin core.
- Bound native inputs, memory, descriptor ownership and syscall surface.
- Preserve generated work when no native file capability is available.

## Options

1. Advertise only generated work. Honest and simple, but leaves the recipient workflow unavailable.
2. Approximate containment with normalized paths and pre-open stats. Rejected because it does not establish descriptor-relative ownership or same-handle link count.
3. Compile a small JNI bridge exposing `openRoot`, `openAlias`, `snapshot`, `pread`, `revalidate`, and `close`.

## Decision

Choose option 3, explicitly approved by the chief architect on 2026-10-30 after the failed public-JDK probe. Native C handles POSIX calls only. The Kotlin `RootedFileProvider` owns the root FD and each `PositionalSource` owns one file FD. No root, source or descriptor identifiers are accepted from the client. The host receives validated flat aliases only.

Root opens use `O_DIRECTORY|O_NOFOLLOW|O_CLOEXEC`; aliases use `openat(rootFd, alias, O_RDONLY|O_NOFOLLOW|O_NONBLOCK|O_CLOEXEC)`. Before admission, `fstat`, `fcntl` and `lseek` establish regular type, exactly one hard link, read-only mode, known size within 128 MiB, and seekability. `snapshot` also supports the root directory to capture its actual opened identity. Native code independently validates aliases and array bounds. Read requests use `pread`, at most 65,536 bytes into a bounded native buffer; no Java array is pinned across an OS read. EOF probes at the exact file cap are allowed so observed growth cannot succeed.

The adapter holds at most 17 file-source slots, including sources opened before core admission; excess opens return `BUSY`. It releases each slot exactly once alongside descriptor closure. The core assumes source ownership on every submit outcome, including duplicate/rejection. The adapter releases failures before that transfer. Closure marks the FD invalid before calling POSIX `close`; close is never retried against a potentially reused descriptor.

After hashing, same-descriptor dev/inode/link count/mode/size/mtime/ctime are compared. `fstatat(rootFd, alias, AT_SYMLINK_NOFOLLOW)` compares the alias to the still-opened file. The root's pathname identity is compared with the pinned root's dev/inode before and after work. All content remains bound to the pinned descriptors even if names move.

## Consequences and limits

The native library is an explicit built artifact; when unavailable, startup reports file capability unavailable and advertises generated work only. No silent path-based fallback exists. The current executed target is macOS arm64; Linux compilation and Windows are not established by the macOS result.

This is a same-user local boundary. Root directory ancestors and output-directory ancestors must remain trusted. Root and alias checks detect observed replacement; they do not guarantee an immutable snapshot or prevent every hostile same-user mutation. A blocked OS file read can delay cooperative cancellation; no replacement worker or thread termination is used. Native failures expose stable codes rather than private paths.

## Confirmation

`CapabilityProbe.java` records the missing public-JDK primitives. `RootedFileProviderTest` covers actual regular/empty files, positional reads, unsafe aliases, symlinks, hard links, FIFO and directory rejection, observed growth/shrink/replacement, renamed root, exact size cap, invalid ABI bounds, output boundaries, 17/18 source slots, and 100-cycle descriptor recovery. Host/CLI tests execute separate JVM processes and verify checksums against an independent Python reference. Test results, exact commands and environment are tracked separately from this architecture approval; this ADR does not itself claim a passing runtime gate.

## References

- [FieldPack contract](../../docs/FIELDPACK.md)
- [Binding build decisions](../../docs/decisions/BUILD-DECISIONS.md)
- [Public JDK capability probe](../probe/CapabilityProbe.java)
- [Native implementation](../host/src/main/c/native_files.c)
