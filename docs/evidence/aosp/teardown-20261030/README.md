# AOSP attempt cost and teardown

The VM and both disks were verified absent at **2026-10-30T22:41:46.211288+00:00** after all final evidence hashes were checked locally and both actual boot PNGs were viewed. The deadline task/queue, dedicated IAM binding/role/service account, firewall, subnet and network were then removed. Final provider lists show no instances, disks, reserved addresses, snapshots, attempt network or deadline queue in the isolated project. The billing project and warning-only budget remain for billing records; no compute/storage resource is retained.

Estimated infrastructure use is **US$6.1723** over 6.2257 hours through verified deletion. Including a US$2 egress allowance and 40% tax/FX reserve gives **US$11.4413**, within the user's total US$50 ceiling. These are estimates; the settled invoice, discounts, credits and actual taxes are unavailable. [Calculation](cost-estimate.json) · [original cost controls](../../../AOSP-CLOUD-BUDGET.md).

[Teardown result](teardown.json) records the bounded cleanup. The six `*-provider-response.json` files preserve actual empty-list stdout bytes. [Provenance](provenance.json) identifies private source-receipt hashes and explicit field selections; account/SSH command metadata remains private. Local hashes establish consistency, not independent execution authenticity.

The [completed custom proof](../custom-20261030/README.md) remains separate from cloud cleanup. No additional runtime tests ran during export, curation or teardown.
