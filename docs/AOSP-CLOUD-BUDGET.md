# Optional AOSP cloud budget

## Authorized limited attempt — 2026-10-30

The user authorized an AOSP attempt with a **total US$50 infrastructure ceiling**, minimum necessary tests, and evidence captured while tests execute. The earlier US$100–200 estimate below is historical planning for a longer campaign, not the budget for this attempt. A complete P01–P09 campaign is not promised within this limit.

Prepare implementation, smoke probes and evidence collection locally before paid runtime. Use one isolated project, one Intel `n2-standard-16` VM, a 50-GiB standard boot disk and a **500-GiB zonal SSD workspace**. The verified regional SSD quota is 500 GiB; Astra chose that layout rather than pay for idle time waiting for quota. The VM must have an **absolute termination timestamp no more than 24 hours after creation**, termination action `DELETE`, and `autoDelete=true` for both attached disks. Verify those applied settings before starting the workload. Do not create reservations, snapshots, extra VMs, retained images, managed groups or a VM service account. No automatic deadline extension or second attempt is authorized by this document.

At the dated list rates, 24 hours of compute and workspace storage is approximately **US$23.60**, plus roughly US$0.19 for standard boot disk and ephemeral external IPv4, and bounded evidence transfer. Billing currency was verified as USD. Reserve substantial headroom inside US$50 for tax, local-currency conversion, network and cleanup. Confirm the resource configuration before provisioning. This is a conservative usage envelope, not a provider-enforced dollar cap or final invoice. Ordinary budget alerts are delayed and do not cap Compute Engine/storage spend.

Use one output tree for the baseline followed by the incremental custom image, shallow/current-branch sync and no source mirror, ccache, duplicate output, distribution archive or image copy. Actual Android 16 source/output fit remains unverified: require at least 180 GiB free after sync, warn below 80 GiB and abort below 40 GiB. Export failure evidence and tear down if the workspace cannot safely accommodate the build.

Stop early for a failed KVM/unchanged-image gate or a build that cannot finish within the remaining time. Capture failures as evidence. Export small logs, manifests, checksums and screenshots during execution; do not transfer multi-gigabyte system images for presentation. Remove the disposable VM and its disks as soon as the required evidence is secured, then verify that no attempt-owned chargeable resources remain. The absolute provider deadline is a backstop, not a reason to leave idle resources running.

The native termination timestamp is cleared if the VM stops or suspends. Therefore, also prepare one independent Cloud Tasks HTTP DELETE for the exact disposable instance at hour23:45, authenticated by a keyless service account whose custom role contains only `compute.instances.delete` in this isolated project. The account is never attached to the VM. Use 300-second retry intervals, bounded retry configuration, and preserve the actual task/queue/IAM receipts. Delete acceptance is asynchronous: verify the instance and both disks are absent. Remove the task/queue and cleanup identity after manual early teardown. See ADR-B14 for retry semantics and time gates. The few Cloud Tasks operations fit the contingency; there is no recurring scheduler job.

Sources: [absolute VM runtime limits](https://docs.cloud.google.com/compute/docs/instances/limit-vm-runtime), [budget alerts](https://docs.cloud.google.com/billing/docs/how-to/budgets), and the price tables below. The actual preflight, source-build, image boot, service tests and cleanup need their own receipts; authorization and a local implementation are not runtime evidence.

## Historical longer-campaign estimate

Researched 2026-10-30. Planning estimate only: no account, paid VM, disk or cloud service has been created. This budget covers infrastructure, not Codex/Astra subscriptions or usage, engineering labor, taxes or a guarantee of completion time.

The [Astra-selected profile](decisions/BUILD-DECISIONS.md) is Intel Google Compute Engine `n2-standard-16`, 64 GB RAM, nested KVM, Ubuntu amd64, 1,000 GiB SSD workspace and a small separate boot disk in `europe-west4`. Actual quota, capacity and KVM/Cuttlefish preflight remain required.

| Component | Dated on-demand list rate / assumption |
|---|---|
| N2 16-vCPU / 64-GiB VM, Netherlands | Approximately US$0.855328 per running hour |
| 1,000-GiB zonal SSD Persistent Disk | US$0.000256164 per GiB-hour, about $6.15/day or $43.04/week |
| Boot disk, network and retained outputs | Additional, dependent on selected disk/transfer/retention |
| Screenshots | No separate capture service required; ordinary VM runtime/output storage only |

Sources: Google's region-specific [general-purpose VM price tables](https://cloud.google.com/products/compute/pricing/general-purpose) and [disk price tables](https://cloud.google.com/compute/disks-image-pricing). Tables default to Iowa; select Netherlands when reproducing the quoted regional rates. USD list prices may change and local-currency billing/VAT may differ. No committed-use or Spot discount is assumed.

Illustrative base cost:

`running_hours × 0.855328 + retained_disk_hours × 1,000 × 0.000256164`

For 60–120 running hours and 1–2 weeks of workspace retention, that is approximately **$94–189 before boot disk, network and tax**. Reserve roughly **$100–200** as an initial infrastructure estimate; it is not a spending authorization or automatic cap. A longer investigation or retaining source/build disks for a month changes the total. The workspace alone costs about $187 for a 730-hour month even while compute is stopped.

Use the Mac and allocated phone for current Android/Flutter/JVM work. Rent the Linux host only for the conditional OS-image milestone, stop compute between sessions, and export required evidence before deliberately removing unneeded cloud disks. Do not claim an emulator supplies physical-device performance evidence. Any future provisioning needs the selected account, permitted access and a user-defined budget; nothing is provisioned by this document.
