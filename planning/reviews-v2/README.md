# Ten independent Astra planning reviews

These are proposal inputs from ten separately tasked GPT-6 Astra agents on 2026-10-30. The root coordinator reconciled them into [Plan v2](../../docs/PLAN-V2.md), [FieldPack contract](../../docs/FIELDPACK.md) and [detailed tickets](../TICKETS.md). They are AI planning reviews, not human implementation review or execution evidence. Conflicting recommendations below are intentionally retained as research history. The selected contract and final tickets take precedence.

Selected decisions: 32 flat aliases; 128 MiB per file **and pack**; 64 KiB reads; 16 KiB manifest; supported regular seekable Android descriptors; generated-only AOSP. Larger pack budgets, nested paths, arbitrary provider streams and privileged AOSP file hashing were not adopted. Estimates in individual proposals are not additive; use the deduplicated leaf rollup.

- [01-product-example](01-product-example.md)
- [02-contract-architecture](02-contract-architecture.md)
- [03-android-files](03-android-files.md)
- [04-flutter-example](04-flutter-example.md)
- [05-performance-tickets](05-performance-tickets.md)
- [06-reliability-security](06-reliability-security.md)
- [07-ticket-decomposition](07-ticket-decomposition.md)
- [08-board-open-source](08-board-open-source.md)
- [09-delivery-replan](09-delivery-replan.md)
- [10-skeptical-review](10-skeptical-review.md)

- [Final contract/ticket review and resolutions](TICKETS-CONTRACT-REVIEW.md)
- [Reference implementation review and rerun](REFERENCE-REVIEW.md)
