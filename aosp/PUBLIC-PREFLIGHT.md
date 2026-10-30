# Optional public Cuttlefish boot preflight

The anonymous pinned build `16644040` provides a matching public host/image pair. This
preflight checks host KVM/Cuttlefish viability; it **cannot** pass P02 source-built baseline
or custom BoundaryLab integration. Root controls whether it fits the $50 attempt budget.

```sh
python3 scripts/aosp-fetch-public.py --probe
python3 scripts/aosp-fetch-public.py --out /srv/downloads
mkdir -p /srv/cuttlefish-public-16644040
cd /srv/cuttlefish-public-16644040
tar -xzf /srv/downloads/cvd-host_package.tar.gz
unzip -q /srv/downloads/aosp_cf_x86_64_only_phone-img-16644040.zip
ANDROID_HOST_OUT="$PWD" ANDROID_PRODUCT_OUT="$PWD" ./bin/launch_cvd \
  --daemon --report_anonymous_usage_stats=n --cpus=4 --memory_mb=8192
```

Leave the real `HOME` unchanged. Run as the host's configured Cuttlefish user with its
KVM/network/render memberships already active. Keep host and images in the same extraction
directory and retain downloader SHA/size/magic receipts. Do not wrap the persistent launch
with a collector that kills the command's process group on exit. Root records actual launch
output, adb boot readiness, fingerprint and Enforcing state, then stops this instance with
the matching `./bin/stop_cvd` before source-built baseline/custom boot.

The downloader accepts only the two named artifacts at the fixed public viewer build,
requires HTTPS and exact `storage.googleapis.com` artifact host, refuses cross-host redirects,
streams to a private temporary file, checks exact known size and magic, computes SHA-256,
and atomically publishes without overwriting. Existing complete files are rehashed; partial
files are never treated as complete. No credentials, account service, cookies or signed URL
queries are logged. Download elapsed time is bounded between read operations and each socket
operation has a 60-second timeout. Its SHA establishes captured bytes, not independent source
reproduction or a published upstream signing assertion.
