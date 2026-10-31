# Desktop FieldPack quick start

The desktop CLI sends jobs to a **separate JVM host** over a Unix-domain socket. The host hashes explicitly selected aliases under its startup root. The CLI creates a portable manifest or verifies separately supplied files against one; it does not hash attachments locally.

**Executed walkthrough target:** macOS 26.5.1 arm64, OpenJDK 17.0.14, Gradle 8.13, Kotlin 2.2.21 and Apple clang 21.0.0. The native file adapter is verified on this target. The separate [hosted CI run](https://github.com/oudaykhaled/boundarylab/actions/runs/41141910969) passed all five lanes, including JVM checks on Ubuntu 24.04 and macOS 15 ARM64. Hosted checks do not extend the physical walkthrough or establish Android/macOS feature parity. Windows is unsupported.

## Build the launchers and native adapter

Run these commands from the repository root, with JDK 17 and a trusted Apple compiler installed through Xcode or its Command Line Tools. `compileNative` builds the checked-in C source locally; it does not fetch a prebuilt native library. Root Gradle configuration also uses the project's pinned Android tooling; see the [repository setup instructions](../README.md) for SDK setup.

```sh
export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
export PATH="$JAVA_HOME/bin:$PATH"
java -version
cc --version
./gradlew :desktop:host:compileNative :desktop:host:installDist :desktop:cli:installDist
```

The resulting launchers are `desktop/host/build/install/host/bin/host` and `desktop/cli/build/install/cli/bin/cli`. The native library is `desktop/host/build/native/libboundarylab_files.dylib`. The following walkthrough uses those installed launchers and an absolute native-library path.

## Create and verify an actual file pack

Paste these blocks **in the same terminal**, from the repository root. Each CLI command starts a new JVM; the background host is another JVM. Keep this terminal open until cleanup.

Create a private runtime directory and two small source attachments:

```sh
export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
export PATH="$JAVA_HOME/bin:$PATH"
BL_REPO="$PWD"
BL_DEMO="$(mktemp -d /tmp/bl-demo.XXXXXX)"
chmod 700 "$BL_DEMO"
mkdir -m 700 "$BL_DEMO/source" "$BL_DEMO/recipient" "$BL_DEMO/runtime"
BL_HOST="$BL_REPO/desktop/host/build/install/host/bin/host"
BL_CLI="$BL_REPO/desktop/cli/build/install/cli/bin/cli"
BL_NATIVE="$BL_REPO/desktop/host/build/native/libboundarylab_files.dylib"
printf 'abc' > "$BL_DEMO/source/abc.txt"
: > "$BL_DEMO/source/empty.bin"
```

The socket's immediate parent must belong to the current user and have permissions **0700**. The host creates a **0600** socket, refuses an existing socket path, and checks the parent ownership/mode. A socket directly in `/tmp` fails this check. The short temporary path also avoids macOS Unix-socket path-length limits.

Start the host with its explicit authorized source root:

```sh
JAVA_OPTS="-Dboundarylab.native.library=$BL_NATIVE" \
  "$BL_HOST" --socket "$BL_DEMO/runtime/source.sock" --root "$BL_DEMO/source" \
  > "$BL_DEMO/source-host.log" 2>&1 &
BL_SOURCE_PID=$!

bl_wait_ready() {
  bl_ready_pid="$1"
  bl_ready_log="$2"
  bl_ready_count=0
  until /usr/bin/grep -q '^READY pid=' "$bl_ready_log"; do
    if ! kill -0 "$bl_ready_pid" 2>/dev/null; then
      cat "$bl_ready_log"
      return 1
    fi
    bl_ready_count=$((bl_ready_count + 1))
    if [ "$bl_ready_count" -ge 100 ]; then
      cat "$bl_ready_log"
      return 1
    fi
    sleep 0.1
  done
}
bl_wait_ready "$BL_SOURCE_PID" "$BL_DEMO/source-host.log"
cat "$BL_DEMO/source-host.log"
```

Continue only after the log shows `READY pid=...`. A `File capability unavailable: UNSUPPORTED` diagnostic means generated work is available but file work is unavailable; check the library path, compiler target and supplied root.

Run the generated workload, then create a manifest outside the source root:

```sh
"$BL_CLI" generated --socket "$BL_DEMO/runtime/source.sock" --bytes 65537 --seed 255
"$BL_CLI" create --socket "$BL_DEMO/runtime/source.sock" \
  --name abc.txt --name empty.bin --output "$BL_DEMO/pack.json"
cat "$BL_DEMO/pack.json"
```

Both CLI commands return exit 0. JSON output includes different `hostPid` and `cliPid` values. The manifest records the SHA-256 values for `abc` without a newline and an empty file. Creation refuses an existing output, including a symlink, and refuses output inside the input root.

Explicitly copy the attachments with their aliases intact, then start a different host on the recipient directory. This copy is the manual transfer step; the manifest does not transfer files.

```sh
cp "$BL_DEMO/source/abc.txt" "$BL_DEMO/source/empty.bin" "$BL_DEMO/recipient/"
JAVA_OPTS="-Dboundarylab.native.library=$BL_NATIVE" \
  "$BL_HOST" --socket "$BL_DEMO/runtime/recipient.sock" --root "$BL_DEMO/recipient" \
  > "$BL_DEMO/recipient-host.log" 2>&1 &
BL_RECIPIENT_PID=$!
bl_wait_ready "$BL_RECIPIENT_PID" "$BL_DEMO/recipient-host.log"
cat "$BL_DEMO/recipient-host.log"
"$BL_CLI" verify --socket "$BL_DEMO/runtime/recipient.sock" --manifest "$BL_DEMO/pack.json"
```

Expected: both entries report `MATCH`, exit 0. The recipient host PID differs from the source host and each CLI process.

Change, truncate and remove only the recipient copy. These verification failures deliberately return exit 1; the conditional commands let the walkthrough continue even in a shell using `set -e`.

```sh
printf 'abd' > "$BL_DEMO/recipient/abc.txt"
if "$BL_CLI" verify --socket "$BL_DEMO/runtime/recipient.sock" --manifest "$BL_DEMO/pack.json"; then
  printf 'Unexpected success\n'
else
  printf 'Verification exit: %s (expected 1; DIGEST_MISMATCH)\n' "$?"
fi

printf 'a' > "$BL_DEMO/recipient/abc.txt"
if "$BL_CLI" verify --socket "$BL_DEMO/runtime/recipient.sock" --manifest "$BL_DEMO/pack.json"; then
  printf 'Unexpected success\n'
else
  printf 'Verification exit: %s (expected 1; SIZE_MISMATCH)\n' "$?"
fi

rm "$BL_DEMO/recipient/abc.txt"
if "$BL_CLI" verify --socket "$BL_DEMO/runtime/recipient.sock" --manifest "$BL_DEMO/pack.json"; then
  printf 'Unexpected success\n'
else
  printf 'Verification exit: %s (expected 1; MISSING)\n' "$?"
fi
```

`empty.bin` continues to report `MATCH`. Clean up only the two hosts and temporary directory created by this walkthrough:

```sh
kill "$BL_SOURCE_PID" "$BL_RECIPIENT_PID"
wait "$BL_SOURCE_PID" 2>/dev/null || true
wait "$BL_RECIPIENT_PID" 2>/dev/null || true
rm -rf "$BL_DEMO"
unset BL_SOURCE_PID BL_RECIPIENT_PID BL_DEMO BL_REPO BL_HOST BL_CLI BL_NATIVE
unset -f bl_wait_ready
```

## Results and support boundaries

| Exit | Meaning |
|---|---|
| 0 | Generated job succeeded, manifest created, or every listed attachment matched |
| 1 | Valid verification completed with one or more nonmatching entries |
| 2 | Invalid input, unavailable capability, lost transport, failed creation or failed publication |

Recipient outcomes are `MATCH`, `MISSING`, `SIZE_MISMATCH`, `DIGEST_MISMATCH` and `UNREADABLE_OR_UNSUPPORTED`. Symlinks, hard links, FIFOs, directories and unreadable/unsupported sources fail explicitly. Files are limited to 128 MiB each and in aggregate, with at most 32 aliases and 64 KiB reads. All manifest entries are validated before attachment jobs are submitted.

The tested boundary is same-user local access. The supplied root and output-directory ancestors must be trusted; the manifest file type must remain stable during its bounded Java open. The native adapter pins opened files and checks observed mutations and replacements, but does not guarantee an atomic snapshot. Cancellation is cooperative. Losing the host loses unresolved volatile work; the CLI never automatically replays it. Matching digests compare bytes and do not authenticate a sender.

A missing native library leaves `generated-sha256-v1` available and rejects file capability explicitly. The [bounded JNI decision](docs/ADR-001-rooted-posix-inputs.md) explains why the public JDK APIs were insufficient on the tested macOS target.

## Evidence

The walkthrough blocks above were executed unchanged through `/bin/zsh` on 2026-10-30 using the already-built launchers, without rerunning Gradle. Source host PID `5955`, recipient host PID `5996`, and CLI PIDs `5968`, `5976`, `6009`, `6164`, `6173` and `6185` were distinct. Creation and the initial verification returned 0; tamper, truncation and deletion each returned 1 with the outcomes shown above. Both hosts stopped and the temporary directory was removed. These PIDs are a receipt of that run, not values to reuse.

- [Installed-launcher smoke receipt](../conformance/desktop/installed-smoke.json): real distinct JVM PIDs, creation/verification, changed/missing inputs, and generated-only behavior with a deliberately absent native library.
- [Final host QA receipt](../docs/evidence/build/crossplatform-qa-host-final-broker-tests.json): 20 host tests passed and both distributions built, with stable desktop source hashes.
- [CLI/core QA receipt](../docs/evidence/build/crossplatform-qa-core-jvm-r3.json): 11 CLI tests and 159 core tests passed; CLI tests cover 127 manifest vectors, 24 generated vectors and a real multi-chunk file checked by the independent Python reference.
- [Conformance instructions and limitations](../conformance/desktop/README.md).

These are desktop execution receipts. Android/Flutter export and manual transfer require their separate handoff evidence. [Ubuntu and macOS hosted JVM checks passed](https://github.com/oudaykhaled/boundarylab/actions/runs/41141910969); physical-device performance and Android/macOS feature parity remain separate, unexecuted gates.
