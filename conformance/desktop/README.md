# Desktop CLI conformance

The JVM recipient uses the shared Kotlin `FieldPackCodec`, then sends sequential jobs to a **separate JVM host** over a Unix-domain socket. The CLI does not open attachments or hash them locally. Python is used only in integration tests as an independent oracle.

Run from the repository root:

```sh
./gradlew :desktop:cli:test
```

The test task compiles the POSIX JNI helper and starts real host and CLI JVM subprocesses. It fails if the required native capability is unavailable; file checks are not silently skipped. The runtime is JVM 17. XML/HTML results are in `desktop/cli/build/test-results/test` and `desktop/cli/build/reports/tests/test`.

Coverage includes canonical `abc`/empty files, a real 131,073-byte binary file spanning three bounded reads with independent Python verification, separate PIDs, generated ramp vectors at chunk/cap boundaries, digest and size mismatch, missing input, symlink/hardlink/FIFO/directory/unreadable-file rejection, per-file and aggregate caps, no-overwrite output publication, output outside the host root, shared strict manifest vectors and absent file capability. Every malformed shared manifest is also supplied to a fresh CLI process with a nonexistent socket: the expected validation error proves it is rejected before connecting or submitting file jobs. The host tests separately own descriptor identity, mutation, cleanup, session lifetime and transport framing evidence.

## Running the applications

Use the [desktop quick start](../../desktop/README.md) for exact tested JDK 17 setup, native-helper/distribution build commands and a complete fixture walkthrough. It starts separate source and recipient hosts in a private 0700 socket directory, runs the installed CLI in separate JVMs, explicitly transfers aliased attachments, and demonstrates matching, changed, truncated and missing files. It also includes cleanup and the macOS-only executed support statement; Linux CI remains pending.

Output is bounded JSON with stable path-free diagnostic codes. Exit 0 means a completed creation, generated result, or all listed files matched. Exit 1 means verification completed with at least one nonmatching entry. Exit 2 means arguments, manifest, capability, transport, creation or publication failed. Session loss prevents complete-pack publication; no reconnect or implicit replay occurs. Separate source files must be transferred manually under their aliases; a manifest never transfers bytes or authenticates a sender.

## Publication and input boundaries

Creation validates aliases before contacting the host and asks the host to validate the output-parent/root relationship before hashing and again before publication. The writer validates the completed manifest, writes a private same-directory temporary file, calls `force(true)`, closes it, and publishes via `Files.createLink`. A racing existing target, including a symlink, is never replaced. Temporary files are removed on ordinary failures; abrupt process termination may leave an unpublished temporary file. Any published output is a complete manifest. Directory fsync/crash durability and a transactional source snapshot are not promised.

The supplied manifest itself is a bounded local input, separate from attachments: it is checked as a regular file with `NOFOLLOW_LINKS`, opened with no-follow semantics, and at most 16,385 bytes are read to enforce the 16,384-byte cap. The caller must trust its directory ancestry and keep its type stable during this open. A hostile race replacing a manifest with a FIFO between metadata checking and opening is outside this manifest-input profile; attachments use the host's descriptor-relative native safety checks instead. Output directory ancestry is likewise assumed stable. See `docs/FIELDPACK.md` for the exact threat model.

These tests provide desktop/reference evidence only. They do not establish an installed Android/Flutter-to-PC manual handoff or close FP06 by themselves.
