#!/usr/bin/env python3
"""Bounded, single-target custom-image checks. Root operator runs after custom boot.
Every adb invocation gets a separate immutable raw receipt before interpretation.
No skipped check becomes passed; source/manifest/build receipts are supplied externally.
"""
import argparse
from datetime import datetime, timezone
import hashlib
import json
import re
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


def verify_raw_fd(records, logs):
    observations = [r['rawFd'] for r in records if 'rawFd' in r]
    check('raw_fd_observation', len(observations) == 1, observations)
    observation = observations[0]
    status = observation.get('status')
    classification = observation.get('classification')
    if classification == 'SERVICE_INVALID':
        check('raw_fd_rejected', status == 0 and observation.get('parsedStatus') == 0 and
              observation.get('replyDecoded') is True and observation.get('exceptionCode') == -8 and
              observation.get('serviceError') == 1, observation)
        return
    if classification == 'PERMISSION_DENIED':
        check('raw_fd_rejected', status == -1 and observation.get('parsedStatus') == -1 and observation.get('replyDecoded') is False, observation)
        return
    check('raw_fd_observation_shape', classification == 'FAILED_TRANSACTION_REQUIRES_AVC' and
          status == -2147483646 and observation.get('parsedStatus') == status and
          observation.get('replyDecoded') is False, observation)
    fields = ('pid', 'stdoutInode', 'startedEpochMs', 'endedEpochMs')
    check('raw_fd_identity_shape', all(type(observation.get(k)) is int and observation[k] > 0 for k in fields)
          and observation['startedEpochMs'] <= observation['endedEpochMs'], observation)
    # Audit timestamps are quantized and logd delivers driver messages asynchronously.
    # One second on either side is fixed; exact PID, inode, audit ID and identities
    # must still match. No missing or unrelated transport error becomes a pass.
    lower = observation['startedEpochMs'] / 1000 - 1
    upper = observation['endedEpochMs'] / 1000 + 1
    events = []
    for line in logs.splitlines():
        match = re.match(r'^\s*(\d+\.\d+)\s+(\d+)\s+(\d+)\s+[VDIWEF]\s+([^:]+):\s*(.*)$', line)
        if match and lower <= float(match[1]) <= upper:
            events.append((int(match[2]), match[4].strip(), match[5], line))
    pid, inode = observation['pid'], observation['stdoutInode']
    avcs = []
    audit_ids = set()
    for event_pid, tag, message, line in events:
        audit = re.search(r'audit\([^)]*:(\d+)\)', message)
        if event_pid == pid and tag in {'auditd', 'boundarylab_pro'} and audit and re.search(r'avc:\s+denied\s+\{\s*use\s*\}', message) and all(
                token in message.split() for token in (
                    'type=1400', f'ino={inode}', 'scontext=u:r:boundarylab:s0', 'tcontext=u:r:adbd:s0',
                    'tclass=fd', 'permissive=0')):
            avcs.append(line)
            audit_ids.add(audit[1])
    check('raw_fd_unique_denial_audit', len(audit_ids) == 1,
          {'observation': observation, 'audit_ids': sorted(audit_ids), 'avcs': avcs})
    audit_id = next(iter(audit_ids))
    identities = [line for event_pid, tag, message, line in events if event_pid == pid and
        tag in {'auditd', 'boundarylab_pro'} and
        re.search(r'audit\([^)]*:' + re.escape(audit_id) + r'\)', message) and
        all(token in message.split() for token in ('type=1300', 'syscall=16', 'uid=2000', 'euid=2000',
            'exe="/system_ext/bin/boundarylab_probe"', 'subj=u:r:boundarylab_probe:s0'))]
    drivers = [line for event_pid, tag, message, line in events if event_pid == 0 and tag == 'rust_binder' and re.search(
        r'Transaction failed: BR_FAILED_REPLY \{ source: EACCES \} my_pid:' + str(pid) + r'\b', message)]
    check('raw_fd_rejected', bool(identities) and len(drivers) == 1,
          {'classification': 'CORRELATED_SELINUX_FD_USE_DENIAL', 'observation': observation,
           'audit_id': audit_id, 'avcs': avcs, 'identities': identities, 'drivers': drivers})


def run_probe(*args, timeout=150):
    smoke = args == ('smoke',)
    log_start = shell('date', '+%m-%d %H:%M:%S.000') if smoke else None
    receipt = adb('shell', shlex.join((probe, *args)), timeout=timeout, required=False)
    # Preserve denial/driver logs even when a later probe case fails. Shell's log
    # access on this userdebug image needs no root or policy expansion.
    logs = shell('logcat', '-b', 'all', '-d', '-v', 'epoch', '-T', log_start) if smoke else ''
    records = [json.loads(line) for line in receipt['stdout'].splitlines() if line.startswith('{')]
    if smoke:
        verify_raw_fd(records, logs)
    check('probe_command:' + ' '.join(args), receipt['exit_code'] == 0, receipt)
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


CORE_COUNTS = ('liveSessions', 'queuedJobs', 'runningJobs', 'retainedEntries', 'retainedBytes')


def quiescent(label):
    # Cooperative cleanup may outlive close/death. Bound the entire observation
    # window, including the probe subprocess, rather than assuming a fixed sleep suffices.
    deadline = time.monotonic() + 5
    observations = []
    while time.monotonic() < deadline:
        records = run_probe('resources', timeout=max(0.001, deadline - time.monotonic()))
        samples = [r['resources'] for r in records if 'resources' in r]
        check(label + ':resource_record', len(samples) == 1, samples)
        sample = samples[0]
        check(label + ':resource_shape', all(type(sample.get(k)) is int and sample[k] >= 0 for k in CORE_COUNTS)
              and type(sample.get('servicePid')) is int and sample['servicePid'] > 0
              and isinstance(sample.get('epoch'), str) and bool(sample['epoch']), sample)
        observations.append({'observed_at': stamp(), 'resources': sample})
        if all(sample[k] == 0 for k in CORE_COUNTS):
            check(label + ':quiescent', True, observations)
            return sample
        time.sleep(min(0.25, max(0, deadline - time.monotonic())))
    check(label + ':quiescent', False, observations)


def resource_snapshot(label):
    # Caller is unrooted so metrics always traverse the authorized UID/domain.
    core = quiescent(label)
    root()
    pid = shell('pidof', 'boundarylab_service')
    check(label + ':pid_matches_metrics', pid.isdigit() and int(pid) == core['servicePid'],
          {'pid': pid, 'resources': core})
    start_ticks = int(shell('cat', f'/proc/{pid}/stat').rsplit(')', 1)[1].split()[19])
    entries = shell('ls', '-1', f'/proc/{pid}/fd').splitlines()
    check(label + ':fd_listing', bool(entries) and all(e.isdigit() for e in entries), entries)
    listing = shell('ls', '-l', f'/proc/{pid}/fd')
    check(label + ':generation_stable', shell('pidof', 'boundarylab_service') == pid and
          int(shell('cat', f'/proc/{pid}/stat').rsplit(')', 1)[1].split()[19]) == start_ticks,
          {'pid': pid, 'start_ticks': start_ticks})
    snapshot = {'observed_at': stamp(), 'pid': int(pid), 'start_ticks': start_ticks,
                'resources': core, 'fd_count': len(entries), 'fd_numbers': sorted(int(e) for e in entries),
                'fd_listing': listing}
    check(label + ':snapshot', True, snapshot)
    unroot()
    return snapshot


try:
    adb('wait-for-device', timeout=45)
    check('profile', shell('getprop', 'ro.boundarylab.profile') == 'generated-v1', shell('getprop', 'ro.build.fingerprint'))
    check('enforcing_before', shell('getenforce') == 'Enforcing', shell('getenforce'))
    shell('getprop')
    unroot()
    run_probe('empty')
    run_probe('known')  # Warm Binder/session/worker/crypto lazy resources before the measured cycle.
    before = resource_snapshot('cycle_before')
    run_probe('smoke')
    quiescent('after_smoke')
    # Deliberately abrupt process exit must revoke the remote token's session.
    leaked = next(r for r in run_probe('leak') if 'sessionId' in r)
    quiescent('after_client_death')
    run_probe('inspect', leaked['epoch'], leaked['sessionId'], leaked['jobId'], 'EXPIRED_OR_UNKNOWN')
    after = resource_snapshot('cycle_after')
    check('resource_cycle_same_generation', before['pid'] == after['pid'] and
          before['start_ticks'] == after['start_ticks'] and
          before['resources']['epoch'] == after['resources']['epoch'], {'before': before, 'after': after})
    check('resource_cycle_returns_to_baseline', before['fd_count'] == after['fd_count'] and
          all(before['resources'][k] == after['resources'][k] == 0 for k in CORE_COUNTS),
          {'fd_delta': after['fd_count'] - before['fd_count'], 'before': before, 'after': after})
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
    fresh = next(r for r in run_probe('known') if 'sessionId' in r)
    check('post_restart_new_epoch', fresh['epoch'] != old['epoch'], {'old': old, 'fresh': fresh})
    final = resource_snapshot('final_after_restart')
    check('final_snapshot_matches_fresh_job', final['resources']['epoch'] == fresh['epoch'], final)
    check('enforcing_after', shell('getenforce') == 'Enforcing', shell('getenforce'))
    check('completed', True, 'Minimum runtime corpus only; no compatibility-evolution/performance/soak claim')
finally:
    save()
