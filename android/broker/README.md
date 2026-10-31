# Android broker

Minimum Android API 30 is required for the public `Os.fcntlInt` primitive.
Runtime support claims apply only to targets recorded by QA.

Application `dev.boundarylab.broker` exports
`dev.boundarylab.broker.BoundaryBrokerService` behind the signature permission
`dev.boundarylab.permission.BIND_BROKER`. Approved clients are separately installed
packages and UIDs. Every AIDL method captures and verifies the calling UID before
policy operations; owner identity is never accepted from request metadata. A
client Binder death token revokes its session and cooperatively cancels work.

## Install an adopter-owned broker

The published Android AAR is a client library; it does not install this service.
Build the broker APK separately and sign it and every approved consumer with the
same private development or product certificate. The default Android debug key
is suitable only for a local matched demonstration. Keep signing keys outside
source control. Independently signed applications are denied.

For a custom application ID and an owned signature permission:

```sh
./gradlew :android:broker:assembleDebug -Pboundarylab.brokerApplicationId=com.example.boundarybroker -Pboundarylab.brokerBindPermission=com.example.boundarybroker.permission.BIND_BROKER
adb install -r android/broker/build/outputs/apk/debug/broker-debug.apk
```

Both properties are optional and retain the defaults above when omitted. The
service class stays `dev.boundarylab.broker.BoundaryBrokerService`; changing the
application ID does not relocate it. The broker declares its configured permission
with fixed `signature` protection and uses the same value on every Binder entry.
A custom permission must be owned by that broker package.

Each consumer supplies the exact component/permission through
[`BrokerEndpoint`](../../sdk/kotlin/README.md), requests the permission with
`<uses-permission>`, and declares a `<queries><package>` entry for the broker in
its own application manifest. The SDK checks both the permission owner/protection
and matching signatures before delegation. See the
[library guide](../../docs/LIBRARY-GUIDE.md) for the complete install sequence and
an independent Android consumer. This application broker is separate from the
[custom OS native service](../../aosp/README.md).

## Descriptor and session contract

The service delegates policy to `dev.boundarylab.core.CoreEngine`, with one worker.
Binder ingress performs bounded metadata/admission work. It never opens a supplied
URI/path and never hashes on a Binder thread. The adapter validates read-only
flags, regular type, seekability, known bounded length and `st_nlink == 1` using
the received descriptor. The declared size is preserved and validated by the core.

`AndroidFileSource` uses `Os.pread` from offset zero with <=64 KiB reads. It compares
same-descriptor device/inode/length/link-count and nanosecond mtime/ctime metadata
after reading. Mutation detection is best effort; no atomic snapshot is promised.
PFD delegates the whole opened object. It cannot reveal an original URI/path or
recover discarded AssetFileDescriptor subsection metadata. Consumers must obtain
whole-file PFDs via `openFileDescriptor`, not extract them from asset subsections.
Android delegation does not claim the POSIX recipient's path/symlink-root checks.

A nonblocking semaphore bounds validated/validation-in-progress broker inputs to
17, including duplicate and rejection paths. Each successful adapter owns its
permit until exactly-once close. Binder may transiently unmarshal more incoming
handles before application admission executes; those are rejected and closed.
The cap is not a claim to control kernel Binder parcel unmarshalling allocations.
The client and broker handles have separate owners; core submission consumes the
broker source on every outcome, including rejection and retained duplicates.

Build and device checks (execute centrally to avoid concurrent Gradle cache writes):

```sh
./gradlew :android:broker:assembleDebug :android:broker:assembleDebugAndroidTest :android:broker:lintDebug
./gradlew :android:broker:connectedDebugAndroidTest
```

`AndroidFileSourceTest` exercises real Android descriptors, shared cursor isolation,
unsupported sources, observed metadata changes, duplicate closure with a different
incoming size, declared-size rejection, and 100 cleanup cycles. `SdkOwnershipTest`
exercises close/rejection and concurrent close/submission. These in-target tests do
not establish cross-package ownership or signature denial. Native consumer and
separately signed fixture instrumentation supply those independent boundaries.
Runtime results belong in QA evidence; unexecuted tests remain unverified.
