#!/usr/bin/env python3
"""Attempt-only helpers; importing this file performs no host/device mutation.

Root creates /srv/aosp/evidence/custom/source-checkpoint.json from actual frozen
BoundaryLab Git source and the staged/frozen overlay BEFORE native/image compile.
Then appends phase_receipts after both phases actually finish. Exact v1 shape:
{
  "schema_version": 1, "kind": "frozen_custom_source_checkpoint",
  "source_commit": "<full 40 lowercase hex BoundaryLab Git commit>",
  "source_inventory_captured_utc": "<aware ISO UTC; before native started_utc>",
  "manifest_commit": "15128c9e27cfa599c48d294babd39286ee8f1426",
  "target": "aosp_cf_x86_64_only_phone-bp4a-userdebug",
  "overlay_files": {"Android.bp": "<sha256>", "...every regular overlay file...": "<sha256>"},
  "baseline_boot_run_sha256": "<baseline/boot-run-result.json SHA256>",
  "baseline_local_export": {
    "path": "baseline-local-export-integrity.json", "sha256": "<SHA256>"
  },
  "phase_receipts": {
    "native": {"path": "native-r1/build-result.json", "sha256": "<SHA256>"},
    "image": {"path": "image-r1/build-result.json", "sha256": "<SHA256>"}
  },
  "expected_native_assertions": 61
}

baseline-local-export-integrity.json is an actual local verification record copied
back by root, NOT an approval. Required keys:
kind="locally_verified_baseline_export", verified=true,
archive_sha256=<locally received archive hash>,
baseline_boot_run_sha256=<same baseline aggregate hash>,
local_verification_utc=<actual aware UTC>, files_verified=<positive integer>.
Root retains the original local archive/inventory. These scripts verify the copied
record/hash and its baseline binding; they cannot independently inspect root's Mac.

overlay_files must cover the entire vendor/boundarylab tree, including the real
Soong-generated aidl_api/ and modified Android.bp, without symlinks. Do not invent
a Git commit, API hash, source timestamp, build result or export-verification result.
No private credentials belong in the checkpoint or VM. Source identity is operator
supplied; host content is independently compared to its frozen inventory here.
"""
import datetime as dt
import hashlib
import json
import os
from pathlib import Path
import re
import signal
import subprocess
import time

BASE = Path('/srv/aosp')
SRC = BASE / 'source'
HOST = SRC / 'out/host/linux-x86'
PRODUCT = SRC / 'out/target/product/vsoc_x86_64_only'
HOME_DIR = BASE / 'home'
CUSTOM = BASE / 'evidence/custom'
OUT = CUSTOM / 'proof'
BASELINE = BASE / 'evidence/baseline'
CHECKPOINT = CUSTOM / 'source-checkpoint.json'
OVERLAY = SRC / 'vendor/boundarylab'
PROFILE = Path('/etc/apparmor.d/boundarylab-crosvm')
MANIFEST = '15128c9e27cfa599c48d294babd39286ee8f1426'
TARGET = 'aosp_cf_x86_64_only_phone-bp4a-userdebug'
NO_NEW_START = dt.datetime(2026, 10, 31, 12, 20, tzinfo=dt.timezone.utc).timestamp()
HARD_END = dt.datetime(2026, 10, 31, 14, 10, tzinfo=dt.timezone.utc).timestamp()
TEST_SHA = '6c5cc1fe0f00342b803d9c01dd80132a0b5e54f4fc1b357927027ff2da7d71b0'
TEST_DEVICE = '/data/nativetest64/boundarylab_engine_test/boundarylab_engine_test'
PREFIX = ['sudo', '--login', '--user=aosp', '--']
BINS = ('boundarylab_service', 'boundarylab_probe', 'boundarylab_denied_probe')


def stamp():
    return dt.datetime.now(dt.timezone.utc).isoformat()


def digest(path):
    value = hashlib.sha256()
    with Path(path).open('rb') as stream:
        for data in iter(lambda: stream.read(1024 * 1024), b''):
            value.update(data)
            if time.time() >= HARD_END:
                raise TimeoutError('original absolute proof deadline reached while hashing')
    return value.hexdigest()


def write(path, value):
    with Path(path).open('x') as stream:
        json.dump(value, stream, indent=2)
        stream.write('\n')
        stream.flush()
        os.fsync(stream.fileno())


def require(ok, message):
    if not ok:
        raise RuntimeError(message)


def sha(value):
    require(isinstance(value, str) and re.fullmatch('[0-9a-f]{64}', value), 'invalid SHA256')
    return value


def timestamp(value):
    parsed = dt.datetime.fromisoformat(value)
    require(parsed.utcoffset() == dt.timedelta(0), 'timestamp must explicitly use UTC')
    return parsed.timestamp()


def inventory(root):
    require(root.is_dir() and not root.is_symlink(), 'invalid inventory root')
    result = {}
    for path in sorted(root.rglob('*')):
        require(not path.is_symlink(), 'symlink in frozen overlay: ' + str(path))
        if path.is_file():
            result[str(path.relative_to(root))] = digest(path)
        else:
            require(path.is_dir(), 'nonregular object in overlay')
    return result


def referenced_record(ref):
    require(set(ref) == {'path', 'sha256'}, 'receipt reference shape mismatch')
    relative = Path(ref['path'])
    require(not relative.is_absolute() and '..' not in relative.parts, 'unsafe receipt reference')
    path = CUSTOM / relative
    require(path.resolve().is_relative_to(CUSTOM.resolve()) and not path.is_symlink(), 'receipt escapes custom evidence')
    require(digest(path) == sha(ref['sha256']), 'receipt hash changed: ' + str(relative))
    return path, json.loads(path.read_text())


def gates():
    require(os.geteuid() == 0, 'root operator required')
    require(time.time() < NO_NEW_START, 'no new custom proof after 2026-10-31T12:20:00Z')
    require(not CHECKPOINT.is_symlink(), 'checkpoint must not be symlink')
    cp = json.loads(CHECKPOINT.read_text())
    require(cp.get('schema_version') == 1 and cp.get('kind') == 'frozen_custom_source_checkpoint', 'checkpoint schema mismatch')
    require(re.fullmatch('[0-9a-f]{40}', cp['source_commit']) is not None, 'full actual source Git commit required')
    require(cp['manifest_commit'] == MANIFEST and cp['target'] == TARGET, 'pinned source target mismatch')
    baseline_file = BASELINE / 'boot-run-result.json'
    require(digest(baseline_file) == sha(cp['baseline_boot_run_sha256']), 'baseline aggregate changed')
    baseline = json.loads(baseline_file.read_text())
    require(baseline.get('kind') == 'unchanged_source_boot_and_cleanup' and baseline.get('passed') is True and
            baseline.get('boot_passed') is True and baseline.get('cleanup_verified') is True and not baseline.get('error'),
            'actual unchanged baseline boot AND cleanup must pass')
    build = json.loads((BASELINE / 'build-result.json').read_text())
    require(build.get('kind') == 'unchanged_source_build' and build.get('exit_code') == 0 and
            build.get('stop_reason') is None and build.get('overlay_present') is False and
            build['cleanup'].get('group_exists_after_cleanup') is False, 'unchanged baseline build prerequisite failed')
    _, exported = referenced_record(cp['baseline_local_export'])
    require(exported.get('kind') == 'locally_verified_baseline_export' and exported.get('verified') is True and
            exported.get('baseline_boot_run_sha256') == cp['baseline_boot_run_sha256'] and
            type(exported.get('files_verified')) is int and exported['files_verified'] > 0,
            'concrete locally verified baseline export receipt required')
    sha(exported['archive_sha256'])
    require(timestamp(exported['local_verification_utc']) <= time.time(), 'local export verification is in future')
    frozen_at = timestamp(cp['source_inventory_captured_utc'])
    require(frozen_at <= time.time(), 'source inventory timestamp is in future')
    require(cp['overlay_files'] and inventory(OVERLAY) == cp['overlay_files'], 'frozen overlay inventory differs')
    require(cp.get('expected_native_assertions') == 61 and
            cp['overlay_files'].get('tests/engine_test.cpp') == TEST_SHA,
            'expected native61 count is tied to reviewed engine_test.cpp; changed test requires review')
    require((OVERLAY / 'aidl_api/boundarylab-platform/1/.hash').is_file() and
            bool((OVERLAY / 'aidl_api/boundarylab-platform/1/.hash').read_text().strip()), 'real frozen V1 API missing')
    previous_end = frozen_at
    for phase in ('native', 'image'):
        path, receipt = referenced_record(cp['phase_receipts'][phase])
        candidates = []
        for directory in CUSTOM.glob(phase + '-r*'):
            match = re.fullmatch(phase + r'-r([1-5])', directory.name)
            require(match is not None and directory.is_dir(), 'unexpected phase attempt directory')
            candidates.append((int(match[1]), directory / 'build-result.json'))
        require(candidates and max(candidates)[1] == path, 'selected receipt is not explicit latest ' + phase)
        require(receipt.get('kind') == 'custom_source_build_step' and receipt.get('phase') == phase and
                receipt.get('attempt') == str(max(candidates)[0]) and receipt.get('exit_code') == 0 and
                receipt.get('stop_reason') is None and receipt['cleanup'].get('group_exists_after_cleanup') is False and
                receipt.get('target') == TARGET and receipt.get('source_manifest') == 'android-16.0.0_r4' and
                receipt.get('out_dir') == str(SRC / 'out') and receipt.get('overlay_present') is True,
                'latest ' + phase + ' build/cleanup is not successful')
        began, ended = timestamp(receipt['started_utc']), timestamp(receipt['ended_utc'])
        require(previous_end <= began <= ended <= time.time(), 'phase order/source capture chronology mismatch')
        require(digest(path.parent / 'build.log') == receipt['log_sha256'], 'phase build log changed')
        previous_end = ended
    return cp


def stage(name, argv, limit, output=OUT, env=None, cleanup_group=True):
    """Finite owned process group; logs and a command receipt survive failures."""
    require(limit > 0, 'stage window already expired')
    began = time.monotonic()
    proc = None
    error = None
    timed_out = False
    with (output / (name + '.stdout.log')).open('xb') as stdout, (output / (name + '.stderr.log')).open('xb') as stderr:
        try:
            proc = subprocess.Popen(argv, stdout=stdout, stderr=stderr, start_new_session=True, env=env)
            try:
                proc.wait(timeout=limit)
            except subprocess.TimeoutExpired:
                timed_out = True
                error = 'stage timeout'
            except BaseException as exc:
                error = repr(exc)
                raise
        finally:
            if proc:
                # Launch-daemon children leave the launch group; stop_cvd owns their cleanup.
                if cleanup_group or timed_out or error or proc.poll() is None:
                    try:
                        os.killpg(proc.pid, signal.SIGKILL)
                    except ProcessLookupError:
                        pass
                proc.wait(timeout=5)
            receipt = {'argv': argv, 'started_utc': dt.datetime.fromtimestamp(time.time() - (time.monotonic() - began), dt.timezone.utc).isoformat(),
                       'ended_utc': stamp(), 'exit_code': proc.returncode if proc else None,
                       'timed_out': timed_out, 'error': error, 'duration_seconds': time.monotonic() - began}
            write(output / (name + '-command.json'), receipt)
    return receipt


def success(receipt):
    return receipt.get('exit_code') == 0 and not receipt.get('timed_out') and not receipt.get('error')


def cvd_rows(text):
    rows = []
    for line in text.splitlines():
        fields = line.split(None, 2)
        if len(fields) < 3:
            continue
        comm = fields[1].strip('[]')
        if comm == 'adb' or comm.startswith(('crosvm', 'run_cvd', 'launch_cvd', 'assemble_cvd', 'cvd_internal')) or str(HOST) in fields[2]:
            rows.append(line)
    return rows
