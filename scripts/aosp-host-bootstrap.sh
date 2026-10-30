#!/usr/bin/env bash
# Run only as root on the disposable, authorized Ubuntu 24.04 x86_64 VM.
# This script never provisions cloud resources or changes their lifetime.
set -euo pipefail
test "$(id -u)" -eq 0
test "$(uname -m)" = x86_64
. /etc/os-release
test "$ID" = ubuntu
test "$VERSION_ID" = 24.04

workspace_device=/dev/disk/by-id/google-aosp-workspace
test -b "$workspace_device"
mkdir -p /srv/aosp
if ! mountpoint -q /srv/aosp; then
    # A blank, attempt-owned data disk only. Refuse to overwrite a filesystem.
    if ! blkid "$workspace_device" >/dev/null 2>&1; then
        mkfs.ext4 -m 0 -L aosp-workspace "$workspace_device"
    fi
    test "$(blkid -s TYPE -o value "$workspace_device")" = ext4
    mount -o noatime "$workspace_device" /srv/aosp
fi
test "$(findmnt -n -o LABEL /srv/aosp)" = aosp-workspace
workspace_uuid=$(blkid -s UUID -o value "$workspace_device")
if ! findmnt --fstab --target /srv/aosp >/dev/null; then
    printf 'UUID=%s /srv/aosp ext4 defaults,noatime,nofail 0 2\n' "$workspace_uuid" >> /etc/fstab
fi

export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get install -y --no-install-recommends \
    git-core gnupg flex bison build-essential zip curl ca-certificates \
    zlib1g-dev libc6-dev-i386 libx11-dev lib32z1-dev libgl1-mesa-dev \
    libxml2-utils xsltproc unzip fontconfig python3 python3-venv \
    rsync jq pkg-config libssl-dev repo

# Official Google repository, scoped signing key, and no general apt trust change.
curl --fail --silent --show-error --location \
    https://us-apt.pkg.dev/doc/repo-signing-key.gpg \
    -o /usr/share/keyrings/boundarylab-cuttlefish.asc
chmod 0644 /usr/share/keyrings/boundarylab-cuttlefish.asc
printf '%s\n' 'deb [signed-by=/usr/share/keyrings/boundarylab-cuttlefish.asc] https://us-apt.pkg.dev/projects/android-cuttlefish-artifacts android-cuttlefish main' \
    > /etc/apt/sources.list.d/boundarylab-cuttlefish.list
apt-get update
# Install only the required host resources; screenshots use adb screencap.
apt-get install -y --no-install-recommends cuttlefish-base=1.57.0

if ! id aosp >/dev/null 2>&1; then
    useradd --create-home --home-dir /srv/aosp/home --shell /bin/bash aosp
fi
for group in kvm cvdnetwork render; do
    getent group "$group" >/dev/null
    usermod -aG "$group" aosp
done
test ! -L /srv/aosp/source/out
mkdir -p /srv/aosp/source /srv/aosp/evidence /srv/aosp/preflight /srv/aosp/source/out
chown aosp:aosp /srv/aosp/source /srv/aosp/evidence /srv/aosp/preflight /srv/aosp/source/out
chmod 0755 /srv/aosp/source/out
runuser -u aosp -- test -w /srv/aosp/source/out
if ! modprobe kvm_intel || ! modprobe vhost_vsock; then
    apt-get install -y --no-install-recommends linux-modules-extra-"$(uname -r)"
    modprobe kvm_intel
    modprobe vhost_vsock
fi
udevadm control --reload-rules
udevadm trigger

printf '%s\n' 'Bootstrap complete; run the non-root KVM preflight before fetching images/source.'
uname -a
df -B1 /srv/aosp
dpkg-query -W cuttlefish-base
