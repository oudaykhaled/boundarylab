#!/usr/bin/env python3
"""Read-only cost envelope and applied-instance checks for the authorized attempt."""
import argparse
import datetime as dt
import json
from pathlib import Path

HOURS = 24
LIMIT_USD = 50
RATES = {"vm": 0.855328, "ssdGiB": 0.000256164,
         "standardGiB": 0.000060274, "externalIPv4": 0.005}


def check(instance, deadline):
    failures = []
    def require(value, message):
        if not value:
            failures.append(message)
    scheduling = instance.get("scheduling", {})
    applied = scheduling.get("terminationTime", "")
    try:
        same_deadline = (dt.datetime.fromisoformat(applied.replace("Z", "+00:00")) ==
                         dt.datetime.fromisoformat(deadline.replace("Z", "+00:00")))
    except (ValueError, TypeError):
        same_deadline = False
    require(same_deadline, "absolute termination timestamp differs")
    require(scheduling.get("instanceTerminationAction") == "DELETE", "termination must DELETE")
    require(scheduling.get("automaticRestart") is False, "automatic restart must be disabled")
    require(instance.get("machineType", "").endswith("/n2-standard-16"), "unexpected machine type")
    require(instance.get("advancedMachineFeatures", {}).get("enableNestedVirtualization") is True,
            "nested virtualization missing")
    require(not instance.get("serviceAccounts"), "VM must have no cloud service account")
    require(not instance.get("deletionProtection", False), "deletion protection prevents cleanup")
    disks = instance.get("disks", [])
    require(len(disks) == 2, "expected exactly boot and workspace disks")
    require(all(d.get("autoDelete") is True for d in disks), "all disks must auto-delete")
    require(sum(d.get("boot") is True for d in disks) == 1, "expected exactly one boot disk")
    require(any(d.get("deviceName") == "aosp-workspace" for d in disks), "workspace device missing")
    metadata = {m["key"]: m.get("value", "") for m in instance.get("metadata", {}).get("items", [])}
    require(metadata.get("block-project-ssh-keys") == "true", "unrelated project SSH keys allowed")
    require(metadata.get("enable-guest-attributes") == "true", "host-key discovery unavailable")
    require(len(instance.get("networkInterfaces", [])) == 1, "unexpected network count")
    if instance.get("networkInterfaces"):
        nic = instance["networkInterfaces"][0]
        require(nic.get("network", "").endswith("/boundarylab-aosp-net"), "unexpected network")
        require(len(nic.get("accessConfigs", [])) == 1, "unexpected external IPv4 count")
    return failures


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--instance-json", type=Path)
    parser.add_argument("--deadline", help="exact UTC timestamp supplied to Google Cloud")
    args = parser.parse_args()
    infrastructure = HOURS * (RATES["vm"] + 500 * RATES["ssdGiB"]
                              + 50 * RATES["standardGiB"] + RATES["externalIPv4"])
    # Reserve 2 USD of egress, 40% for taxes/conversion, then 10 USD contingency.
    projected = (infrastructure + 2) * 1.4 + 10
    failures = []
    if projected > LIMIT_USD:
        failures.append("cost envelope exceeds authorization")
    if args.instance_json:
        if not args.deadline:
            parser.error("--deadline is required with --instance-json")
        deadline = dt.datetime.fromisoformat(args.deadline.replace("Z", "+00:00"))
        remaining = (deadline - dt.datetime.now(dt.timezone.utc)).total_seconds()
        if not 0 < remaining <= HOURS * 3600:
            failures.append("deadline is expired or more than 24h away")
        value = json.loads(args.instance_json.read_text())
        instance = value[0] if isinstance(value, list) and len(value) == 1 else value
        failures.extend(check(instance, args.deadline))
    print(json.dumps({"schemaVersion": 1, "authorizedTotalUsd": LIMIT_USD,
                      "maximumHours": HOURS, "estimatedInfrastructureUsd": round(infrastructure, 4),
                      "conservativeEnvelopeUsd": round(projected, 4),
                      "failures": failures, "passed": not failures,
                      "limitations": ["Not a provider-enforced dollar cap or settled invoice.",
                                      "Disk size/type and firewall scope require separate API receipts.",
                                      "No second VM, retained disk, snapshot or deadline extension."]}, indent=2))
    return bool(failures)


if __name__ == "__main__":
    raise SystemExit(main())
