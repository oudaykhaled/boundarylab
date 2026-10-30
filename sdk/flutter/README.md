# BoundaryLab Flutter plugin

Android implementation for Flutter 3.38.9 / Dart 3.10.8. `BoundaryLab` owns one
native client per Flutter engine. The native adapter uses `:sdk:kotlin` and the
separate signature-protected `dev.boundarylab.broker` application. It never hashes
in Dart or inside the plugin.

The consumer Gradle build must include the repository's `:sdk:kotlin` and `:core`
projects. See `../../examples/flutter/android/settings.gradle.kts` for a working
local consumer. Both application and broker must have matching signing identity.
No signing material belongs in this repository.

`select()` presents Android's document picker. Native code retains URIs; Dart
receives engine-scoped opaque selection tokens, safe public aliases, and a bounded
provider display name for local source-to-alias mapping. Display names never enter
the manifest. `prepare()` opens each selected URI natively with read authorization,
passes the descriptor to the SDK, and polls sequential jobs at 250 ms or slower.
The broker validates supported descriptors. Providers returning pipes or unknown
lengths are unsupported. `generated()` uses the same SDK and separate broker.

`export()` offers a new SAF document only after the current complete attempt
succeeds. Native shared `FieldPackCodec` encodes the sorted manifest. Success is
reported after destination write, flush and close. A failed write attempts to
remove the newly created document; provider cleanup may fail. SAF providers do
not offer the POSIX atomic no-replace publication guarantee. No crash durability
is claimed. Destination cancellation keeps the complete attempt available.

Cancellation invalidates export immediately and waits for pending provider open
or broker work to reach its observed terminal outcome. Reads may block; there is
no invented hard cleanup deadline. Broker loss, engine disposal, selection changes
and new attempts discard old results. Activity recreation never resubmits a job.
Engine disposal closes only its own SDK session and native selections.

Validation: run `flutter test` and `dart analyze` here. These mock-channel tests
verify facade behavior only. Installed multi-engine and real Binder evidence is
produced separately from the sample Android instrumentation tests.
