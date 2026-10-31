# Published-library Android boundary checks

These maintainer-authored fixtures demonstrate local Maven artifact consumption
by an outside-checkout application. They do not establish independent adoption or
native AOSP execution.

Build the copied `examples/standalone/android` project with `assembleDebug` and
`assembleDebugAndroidTest`, its documented local Maven repository and custom
broker properties. Install app/test APKs, the matching-signer configured broker,
the default broker/test APK and the existing native debug consumer (required by
`CrossOwnerTest`). Select a disposable emulator explicitly. The runner does not
provision, install, resign, uninstall, root or wipe anything.

```sh
python3 conformance/library/run-android.py --serial DEVICE --output /tmp/library-qa/positive --phase consumer
python3 conformance/library/run-android.py --serial DEVICE --output /tmp/library-qa/default --phase broker
python3 conformance/library/run-android.py --serial DEVICE --output /tmp/library-qa/custom --phase endpoint --target-package CUSTOM_BROKER_PACKAGE
```

The consumer phase exercises configured identity, distinct UID/process, matching
signatures, real submit/status, a known SHA-256 oracle, a read-only file descriptor
and manifest round-trip, the original constructor and wrong-permission denial.
Controlled binding contexts in broker tests are labeled in source; they do not
provide additional cross-package IPC proof.

Build/install the independently signed `android/denied-client` as documented in
that module, then run `--phase denied-client` against the default broker. Android
must throw `SecurityException` before exposing a Binder.

For outgoing signer denial, on a **disposable emulator only**, replace the custom
broker with the same package signed by a fresh independent signer. Keep the
consumer's original signer and select `--phase wrong-signer`. It requires real
PackageManager signer inequality, SDK `DENIED`, and local PFD closure. Restore the
matching-signer broker and rerun `--phase consumer`. Never replace a user's app to
prepare this phase. Keep private signing material outside repo/evidence; retain
only public certificate and APK digests. The negative test skips in a normal suite
unless `-e expectedWrongSigner true` is supplied.

Exercise the SAF UI separately: push an owned known `.txt` fixture to the fresh
emulator's Downloads directory, launch the standalone app, use **Choose document
and hash**, select that file through Android's picker, then **Export FieldPack
manifest** through the create-document picker. Retain the app success status,
exported manifest and fixture. Verify them with the independent Python or
published JVM recipient. File-descriptor instrumentation does not substitute for
the actual picker path.

Each runner invocation retains exact timestamped commands, instrumentation output,
per-test events, filtered logcat and output digest. Record source/APK hashes,
certificate digests, device/API and SAF/recipient results in the campaign report.
A green run applies only to those exact artifacts and target.
