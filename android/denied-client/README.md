# Separately signed authorization-denial probe

This test-only application has its own package, `dev.boundarylab.deniedclient`. It requests the broker's signature permission, but is signed using a fresh disposable key distinct from the broker's normal debug key. It does not declare or replace the broker package or permission.

The instrumentation test requires the real broker installed first. It verifies that the service exists, is enabled/exported and protects binding with the expected permission; that the signer differs; and that the permission is denied. It then performs an actual explicit `bindService` and requires `SecurityException` before any broker Binder or session is received. Missing broker, accidentally shared signing, a false return without an exception, or successful bind all fail the test.

Build preparation, from the repository root:

```sh
source "$(python3 scripts/prepare-denied-signing.py)"
./gradlew --no-daemon :android:denied-client:assembleDebug :android:denied-client:assembleDebugAndroidTest
```

The preparation script emits only an environment-file path. It creates random credentials and a seven-day RSA certificate under a private temporary directory outside the repository, with directory mode 0700 and key/environment files mode 0600. Passwords go to `keytool` through environment variables, never command arguments. Reuse that same environment file for a verification attempt; generating another signer requires uninstalling the previous test packages before installing the replacement. Never source, copy or upload these private files as public evidence. CI explicitly prepares its independent signer and builds both APKs, but does not claim device execution.

The assigned runtime QA owner installs with `adb install -t` (the manifest is test-only), then executes `dev.boundarylab.deniedclient.test/androidx.test.runner.AndroidJUnitRunner`. Device selection, broker installation and any prior-probe cleanup belong to that owner. Record the broker/probe public certificate SHA-256 digests, APK SHA-256 hashes, instrumented test result and non-identifying target details. These are safe public evidence; the keystore and passwords are not. Compilation alone does not prove denial.

Primary contract: [Android custom signature permissions](https://developer.android.com/guide/topics/permissions/defining) and [Context.bindService](https://developer.android.com/reference/android/content/Context#bindService(android.content.Intent,%20android.content.ServiceConnection,%20int)).
