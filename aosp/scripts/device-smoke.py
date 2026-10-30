#!/usr/bin/env python3
"""Bounded, single-target custom-image checks. Root operator runs after custom boot.
Every adb invocation gets a separate immutable raw receipt before interpretation.
No skipped check becomes passed; source/manifest/build receipts are supplied externally.
"""
import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import subprocess
import shlex
import time

p = argparse.ArgumentParser()
p.add_argument('--serial', required=True)
p.add_argument('--out', required=True, type=Path)
p.add_argument('--sepolicy-analyze', required=True, type=Path, help='Pinned AOSP host sepolicy-analyze binary')
a = p.parse_args()
a.out.mkdir(parents=True, exist_ok=False)
checks = []
sequence = 0
probe = '/system_ext/bin/boundarylab_probe'


def stamp():
    return datetime.now(timezone.utc).isoformat()


def save():
    (a.out / 'checks.json').write_text(json.dumps({'schema_version': 1, 'serial': a.serial,
        'scope': 'custom-image-runtime', 'checks': checks}, indent=2) + '\n')


def check(name, passed, actual):
    checks.append({'case': name, 'passed': bool(passed), 'actual': actual, 'observed_at': stamp()})
    save()
    if not passed:
        raise RuntimeError(f'Failed: {name}: {actual}')


def adb(*args, timeout=30, required=True):
    global sequence
    sequence += 1
    command = ['adb', '-s', a.serial, *args]
    started = stamp()
    try:
        r = subprocess.run(command, capture_output=True, timeout=timeout)
        receipt = {'argv': command, 'started_at': started, 'ended_at': stamp(), 'exit_code': r.returncode,
                   'stdout': r.stdout.decode(errors='replace'), 'stderr': r.stderr.decode(errors='replace')}
    except subprocess.TimeoutExpired as error:
        receipt = {'argv': command, 'started_at': started, 'ended_at': stamp(), 'exit_code': None,
                   'timeout': timeout, 'stdout': (error.stdout or b'').decode(errors='replace'),
                   'stderr': (error.stderr or b'').decode(errors='replace')}
    raw = json.dumps(receipt, indent=2) + '\n'
    file = a.out / f'{sequence:03d}-command.json'
    file.write_text(raw)
    (a.out / f'{sequence:03d}-command.sha256').write_text(hashlib.sha256(raw.encode()).hexdigest() + '\n')
    if required and receipt['exit_code'] != 0:
        check(f'command_{sequence}', False, receipt)
    return receipt


def shell(*args, **kwargs):
    return adb('shell', shlex.join(args), **kwargs)['stdout'].strip()


def run_probe(*args):
    out = shell(probe, *args, timeout=150)
    records = [json.loads(line) for line in out.splitlines() if line.startswith('{')]
    for record in records:
        if 'case' in record:
            check('probe:' + record['case'], record['passed'], record)
    return records


def unroot():
    adb('unroot')
    adb('wait-for-device', timeout=45)
    check('adb_uid_shell', shell('id', '-u') == '2000', shell('id'))


def root():
    adb('root')
    adb('wait-for-device', timeout=45)
    check('adb_uid_root_for_lifecycle_only', shell('id', '-u') == '0', shell('id'))


try:
    adb('wait-for-device', timeout=45)
    check('profile', shell('getprop', 'ro.boundarylab.profile') == 'generated-v1', shell('getprop', 'ro.build.fingerprint'))
    check('enforcing_before', shell('getenforce') == 'Enforcing', shell('getenforce'))
    shell('getprop')
    unroot()
    run_probe('empty')
    run_probe('smoke')
    run_probe('empty')
    # Deliberately abrupt process exit must revoke the remote token's session.
    leaked = next(r for r in run_probe('leak') if 'sessionId' in r)
    time.sleep(0.5)
    run_probe('inspect', leaked['epoch'], leaked['sessionId'], leaked['jobId'], 'EXPIRED_OR_UNKNOWN')
    run_probe('empty')
    old = next(r for r in run_probe('snapshot') if 'sessionId' in r)
    root()
    # No log clearing: use a bounded time window and preserve raw output.
    denial_start = shell('date', '+%m-%d %H:%M:%S.000')
    shell('setprop', 'ctl.start', 'boundarylab_denied_probe')
    time.sleep(1)
    denied_logs = shell('logcat', '-d', '-v', 'brief', '-T', denial_start, '-s', 'BoundaryLabProbe:I', '*:S')
    check('denied_real_uid', '"case":"denied_real_uid","passed":true' in denied_logs, denied_logs)
    check('denied_domain', '"case":"denied_domain","passed":true' in denied_logs, denied_logs)
    check('denied', '"case":"denied","passed":true' in denied_logs, denied_logs)
    # Run exact native policy tests on the custom image too (including deterministic barriers).
    shell('/data/nativetest64/boundarylab_engine_test/boundarylab_engine_test', timeout=150)
    identities = shell('ps', '-AZ')
    check('daemon_domain', any('u:r:boundarylab:s0' in line and 'boundarylab_service' in line for line in identities.splitlines()), identities)
    # Loaded policy is the authority for per-domain permissive flags.
    loaded_policy = a.out / 'loaded-policy.bin'
    adb('pull', '/sys/fs/selinux/policy', str(loaded_policy))
    policy_command = [str(a.sepolicy_analyze), str(loaded_policy), 'permissive']
    policy_result = subprocess.run(policy_command, capture_output=True, text=True, timeout=30)
    (a.out / 'permissive-domains.json').write_text(json.dumps({'argv': policy_command,
        'exit_code': policy_result.returncode, 'stdout': policy_result.stdout, 'stderr': policy_result.stderr}, indent=2))
    domains = set(policy_result.stdout.split())
    check('service_client_domains_enforcing', policy_result.returncode == 0 and
          not domains.intersection({'boundarylab', 'boundarylab_probe', 'boundarylab_denied'}), policy_result.stdout)
    shell('service', 'list')
    shell('cat', '/proc/uptime')
    # Rate limiting is start-to-start, NOT crash-to-start. Read kernel start ticks.
    clock_ticks = int(shell('getconf', 'CLK_TCK'))
    last_pid = shell('pidof', 'boundarylab_service')
    last_start = int(shell('cat', f'/proc/{last_pid}/stat').rsplit(')', 1)[1].split()[19]) / clock_ticks
    for iteration in range(3):
        shell('kill', '-KILL', last_pid)
        deadline = time.monotonic() + 35
        current_pid = ''
        while time.monotonic() < deadline:
            time.sleep(0.25)
            current_pid = shell('pidof', 'boundarylab_service', required=False)
            if current_pid and current_pid != last_pid:
                break
        check(f'restart_pid_{iteration}', bool(current_pid) and current_pid != last_pid, current_pid)
        current_start = int(shell('cat', f'/proc/{current_pid}/stat').rsplit(')', 1)[1].split()[19]) / clock_ticks
        check(f'restart_period_{iteration}', current_start - last_start >= 9.99,
              {'previous_pid': last_pid, 'pid': current_pid, 'start_interval_seconds': current_start - last_start})
        last_pid, last_start = current_pid, current_start
    unroot()
    run_probe('inspect', old['epoch'], old['sessionId'], old['jobId'], 'STALE_EPOCH')
    run_probe('empty')
    check('enforcing_after', shell('getenforce') == 'Enforcing', shell('getenforce'))
    check('completed', True, 'Minimum runtime corpus only; no compatibility-evolution/performance/soak claim')
finally:
    save()
