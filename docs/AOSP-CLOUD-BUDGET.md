# Optional AOSP cloud budget

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
