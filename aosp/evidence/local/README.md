# Local native implementation checks

Executed on the developer's macOS arm64 host; not Android/AOSP runtime evidence.

- `checks.jsonl`: actual host engine output, 61 assertions passing.
- `environment.txt`: recorded Clang, host architecture and OpenSSL version.
- `build.txt`: compiler stdout/stderr (empty on successful build).
- `sanitizer.txt`: ASan/UBSan stderr (empty).
- `aidl-generation.json`: actual SDK 35 structured CPP generation argv/exit0 and hashes of 4 inputs/16 generated C++ files; local SDK home path redacted with original SHA provenance.
- `aidl-generation.txt`: compiler stdout/stderr (empty).
- `results.json`: scope, exit status and source hashes at receipt preparation.

The native executable is excluded. The installed SDK AIDL generator establishes syntax and
C++ stub generation only. Android 16 Soong API freeze, linking, policy compilation and all
custom-image/device acceptance remain unexecuted in this receipt. Source hashes describe the
receipt preparation state; later reviewed changes require new relevant checks.
