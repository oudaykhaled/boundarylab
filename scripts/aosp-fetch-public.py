#!/usr/bin/env python3
"""Fetch only the pinned anonymous Cuttlefish pair; never print signed storage URLs.
A successful fetch is artifact provenance, not source-build/custom-service evidence.
"""
import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import sys
import tempfile
import time
from urllib.parse import urlparse
from urllib.request import HTTPRedirectHandler, Request, build_opener

BUILD = '16644040'
BASE = f'https://ci.android.com/builds/submitted/{BUILD}/aosp_cf_x86_64_only_phone-userdebug/latest/'
ARTIFACTS = {
    'cvd-host_package.tar.gz': (898832924, b'\x1f\x8b\x08\x00'),
    'aosp_cf_x86_64_only_phone-img-16644040.zip': (1163636385, b'PK\x03\x04'),
}


def allowed(url, host):
    p = urlparse(url)
    return p.scheme == 'https' and p.hostname == host and p.port in (None, 443) and p.username is None and p.password is None


class SameHostRedirect(HTTPRedirectHandler):
    def __init__(self, host):
        self.host = host

    def redirect_request(self, request, fp, code, message, headers, url):
        if not allowed(url, self.host):
            raise RuntimeError('Unexpected redirect host or scheme')
        return super().redirect_request(request, fp, code, message, headers, url)


def open_url(url, host, headers=None):
    if not allowed(url, host):
        raise RuntimeError('Unexpected URL host or scheme')
    return build_opener(SameHostRedirect(host)).open(Request(url, headers=headers or {}), timeout=60)


def artifact_url(viewer):
    with open_url(viewer, 'ci.android.com') as response:
        body = response.read(2 * 1024 * 1024 + 1)
    if len(body) > 2 * 1024 * 1024:
        raise RuntimeError('Artifact viewer exceeds bounded size')
    match = re.search(r'var JSVariables = (\{.*?\});', body.decode(), re.DOTALL)
    if not match:
        raise RuntimeError('Artifact viewer metadata missing')
    url = json.loads(match.group(1))['artifactUrl']
    if not isinstance(url, str) or not allowed(url, 'storage.googleapis.com'):
        raise RuntimeError('Unexpected artifact storage location')
    return url


def verify_file(path, expected_size, magic):
    if path.is_symlink() or not path.is_file() or path.stat().st_size != expected_size:
        raise RuntimeError('Existing artifact is not a complete regular file of the expected size')
    digest = hashlib.sha256()
    with path.open('rb') as stream:
        first = stream.read(4)
        if first != magic:
            raise RuntimeError('Artifact magic mismatch')
        digest.update(first)
        for chunk in iter(lambda: stream.read(1024 * 1024), b''):
            digest.update(chunk)
    return digest.hexdigest()


def fetch(name, out, probe, limit_seconds):
    size, magic = ARTIFACTS[name]
    viewer = BASE + name
    receipt = {'schema_version': 1, 'build_id': BUILD, 'artifact': name, 'viewer_url': viewer,
               'expected_bytes': size, 'started_at': datetime.now(timezone.utc).isoformat()}
    destination = out / name if out else None
    if not probe and destination.exists():
        receipt.update(action='existing_complete_rehashed', bytes=size,
                       sha256=verify_file(destination, size, magic), magic=magic.hex())
        return receipt
    url = artifact_url(viewer)
    if probe:
        with open_url(url, 'storage.googleapis.com', {'Range': 'bytes=0-3'}) as response:
            first = response.read(5)
            content_range = response.headers.get('Content-Range', '')
            if response.status != 206 or content_range != f'bytes 0-3/{size}' or first != magic:
                raise RuntimeError('Range probe status, size, or magic mismatch')
            receipt.update(action='range_probe_only', http_status=response.status,
                           content_range=content_range, received_bytes=len(first), magic=first.hex())
        return receipt
    out.mkdir(parents=True, exist_ok=True)
    fd, partial_name = tempfile.mkstemp(prefix=name + '.', suffix='.partial', dir=out)
    partial = Path(partial_name)
    started = time.monotonic()
    digest = hashlib.sha256()
    count = 0
    try:
        with os.fdopen(fd, 'wb') as target, open_url(url, 'storage.googleapis.com') as response:
            if response.status != 200 or int(response.headers.get('Content-Length', '-1')) != size:
                raise RuntimeError('Artifact status or advertised size mismatch')
            first = response.read(4)
            if first != magic:
                raise RuntimeError('Artifact magic mismatch')
            target.write(first); digest.update(first); count += len(first)
            while True:
                if time.monotonic() - started > limit_seconds:
                    raise RuntimeError('Artifact wall-clock download bound reached')
                chunk = response.read(min(1024 * 1024, size - count + 1))
                if not chunk:
                    break
                count += len(chunk)
                if count > size:
                    raise RuntimeError('Artifact exceeded exact expected size')
                target.write(chunk); digest.update(chunk)
            if count != size:
                raise RuntimeError('Incomplete artifact')
            target.flush(); os.fsync(target.fileno())
        # Atomic publication with no replacement of an existing destination.
        os.link(partial, destination)
        receipt.update(action='downloaded', bytes=count, sha256=digest.hexdigest(), magic=magic.hex())
        return receipt
    finally:
        partial.unlink(missing_ok=True)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--artifact', choices=ARTIFACTS, action='append')
    parser.add_argument('--out', type=Path)
    parser.add_argument('--probe', action='store_true')
    parser.add_argument('--max-download-seconds', type=int, default=1800)
    args = parser.parse_args()
    if not args.probe and args.out is None:
        parser.error('--out is required for downloads')
    if not 1 <= args.max_download_seconds <= 7200:
        parser.error('download wall-clock bound must be 1..7200 seconds')
    for name in args.artifact or ARTIFACTS:
        try:
            receipt = fetch(name, args.out, args.probe, args.max_download_seconds)
            receipt['completed_at'] = datetime.now(timezone.utc).isoformat()
            print(json.dumps(receipt), flush=True)
        except Exception as error:
            # urllib exceptions can embed signed URLs. Never stringify them.
            print(json.dumps({'schema_version': 1, 'artifact': name, 'status': 'failed',
                              'error_type': type(error).__name__,
                              'message': 'Fetch failed; signed URL and query deliberately omitted'}), flush=True)
            return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())
