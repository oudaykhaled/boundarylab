#!/usr/bin/env bash
set -euo pipefail
test "$(id -un)" = aosp
test "$HOME" = /srv/aosp/home
test -d /srv/aosp/source/vendor/boundarylab
test -s /srv/aosp/evidence/custom/proof/preparation.json
python3 -c 'import datetime as d; assert d.datetime.now(d.timezone.utc)<d.datetime(2026,10,31,12,20,tzinfo=d.timezone.utc)'
exec env ANDROID_HOST_OUT=/srv/aosp/source/out/host/linux-x86 \
  ANDROID_SOONG_HOST_OUT=/srv/aosp/source/out/host/linux-x86 \
  ANDROID_PRODUCT_OUT=/srv/aosp/source/out/target/product/vsoc_x86_64_only \
  /srv/aosp/source/out/host/linux-x86/bin/launch_cvd --daemon --report_anonymous_usage_stats=n \
  --system_image_dir=/srv/aosp/source/out/target/product/vsoc_x86_64_only \
  --resume=false --enable_sandbox=true --cpus=4 --memory_mb=8192 \
  --start_webrtc=true --gpu_mode=guest_swiftshader --enable_audio=false
