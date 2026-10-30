# r4 — required Trusty nsjail failure

The unchanged Android16r4 source build failed at84% on2026-10-30T20:56:18UTC.
The required Trusty security-VM genrule could not create its mount namespace under
Ubuntu24 AppArmor's unprivileged-userns restriction. This is a failed result.
The supervisor recorded exit1, no disk/deadline stop and no surviving process group.

The full original log is losslessly compressed as `build.log.gz`; decompress it to
recover the recorded25,259,499bytes and SHA256
`e110eb55d333981a1227b80122f10c50f2bcf1dad3be4c324b9835a7d197359e`.
Manifest, source-status and build-result files retain their exact original bytes.
The original archive and each contained file were verified locally before recovery.
See `provenance.json` for compressed and original hashes.

Astra approved one separate exact-program userns compatibility profile for the
pinned nsjail executable, followed by an incremental resume using the same outputs,
source pin, budget and deadlines. This neither changes source nor establishes full
AppArmor confinement. Earlier compilation's disabled optional build sandbox remains
a recorded limitation. The r5 outcome and any guest boot require their own evidence.
