# Security and privacy scope

BoundaryLab is an open-source implementation candidate for bounded local jobs and file-integrity comparison. No production support or security certification is claimed.

Android uses a signature-protected separate broker, captured caller identity, owner-bound volatile sessions and client-opened descriptors. The SDK authenticates its broker before delegating access. The supported file profile checks read-only regular/seekable/single-link inputs and bounds work/resources; arbitrary provider representations are unsupported. Desktop transport is a private per-user socket directory, with a narrowly reviewed POSIX JNI helper for rooted file access. Trusted directory ancestry and remaining same-user race limits are explicit.

Checksums compare bytes, not authenticity, authorship, malware safety or an immutable acquisition snapshot. No network listener, upload, durable queue, automatic replay or cross-device sync is provided. Cooperative cancellation cannot forcibly interrupt an arbitrary blocked OS read.

See [normative contract](docs/FIELDPACK.md), [critical implementation decisions](docs/decisions/BUILD-DECISIONS.md), [AI security review](docs/evidence/reviews/SECURITY-REVIEW.md) and [AI reliability review](docs/evidence/reviews/RELIABILITY-REVIEW.md). Review artifacts distinguish source inspection, controlled regression cases and actual platform execution; remaining runtime gates remain open.

Avoid posting secrets, private inputs, device serials, private logs or exploitable details publicly. When available, use GitHub private vulnerability reporting; otherwise open an issue requesting a private reporting route without sensitive details. No paid disclosure service or response SLA is offered.
