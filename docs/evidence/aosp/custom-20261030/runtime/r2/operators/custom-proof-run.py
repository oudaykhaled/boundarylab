#!/usr/bin/env python3
"""Root operator: one custom CVD launch, <=600s readiness, one <=900s harness.
Always matching stop_cvd60/adb kill30/process-absence15 after valid prerequisites.
Bad prerequisites reject before launch/cleanup, rather than stopping a baseline.
Original attempt clock: no new start after Oct31 12:20Z; proof stops by14:10Z.
Do not invoke until root exported/verified baseline and ran custom-proof-prepare.py.
No cloud credentials/actions; daemon/probe APKs or binaries are NEVER hot-pushed.
"""
import importlib.util
import json
import os
from pathlib import Path
import re
import shlex
import signal
import subprocess
import time

spec = importlib.util.spec_from_file_location('custom_proof_common', Path(__file__).with_name('custom-proof-common.py'))
c = importlib.util.module_from_spec(spec)
spec.loader.exec_module(c)


def main():
    c.require(os.geteuid() == 0, 'root operator required')
    c.require(c.OUT.is_dir() and not (c.OUT / 'run-result.json').exists() and not (c.OUT / 'run-window.json').exists(),
              'prepared fresh proof directory required; inspect interrupted attempt instead of rerunning')
    started = time.time()
    proof_deadline = min(started + 1500, c.HARD_END - 150)
    boot_deadline = min(started + 600, proof_deadline)
    checks = []
    sequence = 0
    serial = None
    cp = None
    preparation = None
    armed = False
    proof_passed = False
    error = None
    launch = harness = stop = adb_stop = None
    cleanup_verified = False
    observations = []
    native_summary = None

    def interrupted(signum, frame):
        raise InterruptedError('custom proof signal ' + str(signum))

    for sig in (signal.SIGTERM, signal.SIGHUP, signal.SIGINT):
        signal.signal(sig, interrupted)

    def check(name, passed, actual):
        nonlocal sequence
        sequence += 1
        record = {'case': name, 'passed': bool(passed), 'actual': actual, 'utc': c.stamp()}
        checks.append(record)
        c.write(c.OUT / ('check-' + str(sequence).zfill(3) + '.json'), record)
        c.require(passed, 'check failed: ' + name)

    command_sequence = 0

    def command(argv, timeout=30, deadline=None, required=True):
        nonlocal command_sequence
        command_sequence += 1
        name = 'proof-command-' + str(command_sequence).zfill(3)
        limit = min(timeout, (proof_deadline if deadline is None else deadline) - time.time())
        receipt = c.stage(name, argv, limit)
        if required:
            c.require(c.success(receipt), 'command failed: ' + name)
        return receipt, (c.OUT / (name + '.stdout.log')).read_text(errors='replace').strip()

    def adb(*args, **kwargs):
        return command(c.PREFIX + [str(c.HOST / 'bin/adb'), *args], **kwargs)

    def shell(*args, **kwargs):
        c.require(serial is not None, 'no validated explicit serial')
        return adb('-s', serial, 'shell', shlex.join(args), **kwargs)

    def snapshot_png(label):
        device_path = '/data/local/tmp/boundarylab-custom-proof-' + label + '.png'
        shell('screencap', '-p', device_path)
        local = c.OUT / (label + '.png')
        adb('-s', serial, 'pull', device_path, str(local))
        data = local.read_bytes()
        check(label + ':png', 8 < len(data) <= 64 * 1024**2 and data.startswith(b'\x89PNG\r\n\x1a\n'),
              {'bytes': len(data), 'sha256': c.digest(local), 'scope': 'actual custom-image boot screenshot, not native UI interaction'})

    try:
        cp = c.gates()
        preparation = json.loads((c.OUT / 'preparation.json').read_text())
        prepared_result = json.loads((c.OUT / 'preparation-result.json').read_text())
        c.require(prepared_result.get('prepared') is True and preparation.get('prepared') is True and
                  preparation.get('kind') == 'custom_source_boot_prepared' and
                  preparation['checkpoint_sha256'] == c.digest(c.CHECKPOINT), 'actual preparation/checkpoint mismatch')
        for name, expected in preparation['operator_scripts_sha256'].items():
            c.require(Path(name).name == name and c.digest(Path(__file__).parent / name) == expected, 'operator script changed since preparation')
        c.require(c.digest(c.PROFILE) == preparation['apparmor_profile_sha256'], 'AppArmor profile changed after preparation')
        c.require(not (c.HOME_DIR / 'cuttlefish').exists(), 'fresh custom CVD state required')
        for name in ('.cuttlefish_config.json', 'cuttlefish_assembly', 'cuttlefish_runtime', 'cuttlefish_runtime.1'):
            path = c.HOME_DIR / name
            c.require(not path.exists() and not path.is_symlink(), 'unexpected CVD link reappeared after preparation')
        c.require(proof_deadline > started and time.time() < c.NO_NEW_START, 'original start/proof deadline reached')
        armed = True
        c.write(c.OUT / 'run-window.json', {'started_utc': c.stamp(), 'boot_deadline_epoch': boot_deadline,
                                          'proof_deadline_epoch': proof_deadline, 'hard_end_epoch': c.HARD_END,
                                          'no_new_start_epoch': c.NO_NEW_START, 'cleanup_reserve_seconds': 150})
        for key in ('kernel.apparmor_restrict_unprivileged_userns', 'kernel.unprivileged_userns_clone'):
            _, value = command(['sysctl', '-n', key], deadline=boot_deadline)
            check(key, value == '1', value)
        launch = c.stage('custom-launch', c.PREFIX + ['bash', str(Path(__file__).with_name('custom-proof-launch.sh'))],
                         boot_deadline - time.time(), cleanup_group=False)
        c.require(c.success(launch), 'custom launch failed')
        boot_complete = False
        while time.time() < boot_deadline:
            _, devices = adb('devices', '-l', deadline=boot_deadline)
            ready = [line.split()[0] for line in devices.splitlines()[1:]
                     if len(line.split()) >= 2 and line.split()[1] == 'device']
            c.require(len(ready) <= 1, 'multiple ADB targets; refuse implicit selection')
            if ready:
                serial = ready[0]
                _, complete = shell('getprop', 'sys.boot_completed', deadline=boot_deadline)
                if complete == '1':
                    boot_complete = True
                    break
            time.sleep(min(2, max(0, boot_deadline - time.time())))
        check('boot_completed', boot_complete, {'serial': serial})
        props = {}
        for path in (c.PRODUCT / 'system/build.prop', c.PRODUCT / 'system/system/build.prop'):
            if path.is_file():
                for line in path.read_text().splitlines():
                    if '=' in line and not line.startswith('#'):
                        key, value = line.split('=', 1)
                        props[key] = value
        fingerprint_file = c.PRODUCT / 'build_fingerprint.txt'
        expected_fingerprint = fingerprint_file.read_text().strip() if fingerprint_file.is_file() else (
            props.get('ro.system.build.fingerprint') or props.get('ro.build.fingerprint'))
        _, fingerprint = shell('getprop', 'ro.build.fingerprint')
        check('fingerprint_matches_built_system', bool(expected_fingerprint) and fingerprint == expected_fingerprint,
              {'actual': fingerprint, 'expected': expected_fingerprint})
        for name, expected in [('ro.build.version.release', '16'), ('ro.build.version.sdk', '36'),
                               ('ro.build.id', 'BP4A.251205.006'), ('ro.boundarylab.profile', 'generated-v1')]:
            _, actual = shell('getprop', name)
            check(name, actual == expected, actual)
        _, enforcing = shell('getenforce')
        check('selinux_enforcing_before', enforcing == 'Enforcing', enforcing)
        config_path = c.HOME_DIR / 'cuttlefish/instances/cvd-1/cuttlefish_config.json'
        config = json.loads(config_path.read_text())
        c.write(c.OUT / 'effective-config.json', config)
        instance = config['instances']['1']
        for key, expected in [('enable_sandbox', True), ('enable_webrtc', True),
                              ('boot_image', str(c.PRODUCT / 'boot.img')), ('super_image', str(c.PRODUCT / 'super.img')),
                              ('crosvm_binary', str(c.HOST / 'bin/crosvm'))]:
            check('config:' + key, instance.get(key) == expected, instance.get(key))
        # Exact image-installed daemon/probes: never push or replace these files.
        adb('-s', serial, 'root')
        adb('-s', serial, 'wait-for-device', timeout=45)
        _, uid = shell('id', '-u')
        check('root_for_artifact_and_test_placement', uid == '0', uid)
        for name in c.BINS:
            binary = preparation['binaries'][name]
            _, observed = shell('sha256sum', binary['device_path'])
            check(name + ':image_sha256', observed.split()[0] == binary['sha256'], observed)
            _, mode = shell('stat', '-c', '%a:%u:%g', binary['device_path'])
            _, label = shell('ls', '-lZ', binary['device_path'])
            expected_label = {'boundarylab_service': 'boundarylab_exec', 'boundarylab_probe': 'boundarylab_probe_exec',
                              'boundarylab_denied_probe': 'boundarylab_denied_exec'}[name]
            check(name + ':installed_mode_label', mode.split(':')[0] == '755' and ('u:object_r:' + expected_label + ':s0') in label,
                  {'mode_owner': mode, 'label_listing': label})
        snapshot_png('custom-boot')
        # Only the source-built cc_test may be staged if the image omitted it.
        present, _ = shell('test', '-f', c.TEST_DEVICE, required=False)
        c.require(not present['timed_out'] and present['exit_code'] in (0, 1), 'could not inspect native-test presence')
        test = preparation['binaries']['boundarylab_engine_test']
        source_test = c.PRODUCT / c.TEST_DEVICE.lstrip('/')
        check('native_test_source_hash', str(source_test) == test['host_path'] and c.digest(source_test) == test['sha256'], test)
        if present['exit_code'] == 1:
            shell('mkdir', '-p', str(Path(c.TEST_DEVICE).parent))
            adb('-s', serial, 'push', str(source_test), c.TEST_DEVICE)
            shell('chmod', '0755', c.TEST_DEVICE)
            shell('restorecon', '-F', str(Path(c.TEST_DEVICE).parent), c.TEST_DEVICE)
        _, test_hash = shell('sha256sum', c.TEST_DEVICE)
        _, test_mode = shell('stat', '-c', '%a:%u:%g', c.TEST_DEVICE)
        _, test_label = shell('ls', '-lZ', c.TEST_DEVICE)
        check('native_test_device_identity', test_hash.split()[0] == test['sha256'] and test_mode.split(':')[0] == '755' and
              re.search(r'u:object_r:[A-Za-z0-9_]+:s0', test_label) is not None,
              {'source': test, 'device_sha256': test_hash, 'mode_owner': test_mode, 'label_listing': test_label,
               'pushed_only_because_absent': present['exit_code'] == 1})
        adb('-s', serial, 'unroot')
        adb('-s', serial, 'wait-for-device', timeout=45)
        check('overlay_unchanged_before_smoke', c.inventory(c.OVERLAY) == cp['overlay_files'], 'full frozen file inventory comparison')
        smoke_out = c.OUT / 'smoke'
        smoke_script = c.OVERLAY / 'scripts/device-smoke.py'
        harness_argv = c.PREFIX + ['env', 'PATH=' + str(c.HOST / 'bin') + ':/usr/bin:/bin', 'python3', str(smoke_script),
                                 '--serial', serial, '--out', str(smoke_out),
                                 '--sepolicy-analyze', str(c.HOST / 'bin/sepolicy-analyze')]
        harness = c.stage('custom-harness', harness_argv, min(900, proof_deadline - time.time()))
        c.require(c.success(harness), 'single combined harness failed/timed out; do not retry automatically')
        summary = json.loads((smoke_out / 'checks.json').read_text())
        cases = summary.get('checks')
        check('harness_completed_all_passed', summary.get('scope') == 'custom-image-runtime' and summary.get('serial') == serial and
              isinstance(cases, list) and bool(cases) and cases[-1].get('case') == 'completed' and
              all(case.get('passed') is True for case in cases),
              {'checks': len(cases) if isinstance(cases, list) else None, 'checks_sha256': c.digest(smoke_out / 'checks.json')})
        native_receipts = []
        for path in sorted(smoke_out.glob('[0-9][0-9][0-9]-command.json')):
            receipt = json.loads(path.read_text())
            argv = receipt.get('argv', [])
            if len(argv) >= 5 and argv[:3] == ['adb', '-s', serial] and argv[3] == 'shell' and shlex.split(argv[4]) == [c.TEST_DEVICE]:
                native_receipts.append((path, receipt))
        check('one_native_test_execution', len(native_receipts) == 1, [p.name for p, _ in native_receipts])
        native_path, native = native_receipts[0]
        native_records = [json.loads(line) for line in native.get('stdout', '').splitlines() if line.startswith('{')]
        native_cases = [r for r in native_records if 'case' in r]
        summaries = [r for r in native_records if 'summary' in r]
        native_summary = {'receipt': str(native_path.relative_to(c.OUT)), 'receipt_sha256': c.digest(native_path),
                          'expected_count': 61, 'source_sha256': c.TEST_SHA, 'case_count': len(native_cases), 'summaries': summaries}
        check('native61_assertions_and_summary', native.get('exit_code') == 0 and not native.get('timeout') and
              len(native_cases) == 61 and len({r['case'] for r in native_cases}) == 61 and
              all(r.get('passed') is True for r in native_cases) and len(summaries) == 1 and
              summaries[0].get('summary') == 'PASS' and summaries[0].get('checks') == 61, native_summary)
        shell('logcat', '-d', '-t', '2000', '-v', 'brief', '-s', 'BoundaryLabProbe:I', 'avc:W', '*:S')
        snapshot_png('custom-after-smoke')
        _, enforcing = shell('getenforce')
        check('selinux_enforcing_after', enforcing == 'Enforcing', enforcing)
        check('source_and_profile_unchanged', c.inventory(c.OVERLAY) == cp['overlay_files'] and
              c.digest(c.CHECKPOINT) == preparation['checkpoint_sha256'] and
              c.digest(c.PROFILE) == preparation['apparmor_profile_sha256'], 'source checkpoint / overlay / AppArmor unchanged')
        for key in ('kernel.apparmor_restrict_unprivileged_userns', 'kernel.unprivileged_userns_clone'):
            _, actual = command(['sysctl', '-n', key])
            check('final:' + key, actual == '1', actual)
        proof_passed = True
    except BaseException as exc:
        error = repr(exc)
    finally:
        for sig in (signal.SIGTERM, signal.SIGHUP, signal.SIGINT):
            signal.signal(sig, signal.SIG_IGN)
        if armed:
            try:
                stop = c.stage('custom-stop', c.PREFIX + [str(c.HOST / 'bin/stop_cvd')], min(60, c.HARD_END - time.time()))
            except BaseException as exc:
                error = (error or '') + '; stop:' + repr(exc)
            try:
                adb_stop = c.stage('custom-adb-stop', c.PREFIX + [str(c.HOST / 'bin/adb'), 'kill-server'], min(30, c.HARD_END - time.time()))
            except BaseException as exc:
                error = (error or '') + '; adb stop:' + repr(exc)
            until = min(time.monotonic() + 15, time.monotonic() + max(0, c.HARD_END - time.time()))
            while time.monotonic() < until:
                try:
                    result = subprocess.run(['ps', '-u', 'aosp', '-o', 'pid=,comm=,args='], capture_output=True, text=True,
                                            timeout=min(3, max(0.01, until - time.monotonic())))
                    remaining = c.cvd_rows(result.stdout)
                    valid_ps = result.returncode == 0 or (result.returncode == 1 and not result.stdout.strip() and not result.stderr.strip())
                    observations.append({'utc': c.stamp(), 'ps_exit_code': result.returncode, 'owned_processes': result.stdout.splitlines(),
                                         'matching_cvd_processes': remaining, 'stderr': result.stderr})
                    if valid_ps and not remaining:
                        cleanup_verified = True
                        break
                except BaseException as exc:
                    observations.append({'utc': c.stamp(), 'error': repr(exc)})
                time.sleep(min(1, max(0, until - time.monotonic())))
            c.write(c.OUT / 'cleanup-processes.json', observations)
        passed = bool(armed and proof_passed and not error and stop and c.success(stop) and adb_stop and c.success(adb_stop) and
                      cleanup_verified and time.time() <= c.HARD_END and checks and all(item['passed'] for item in checks))
        result = {'kind': 'custom_source_boot_smoke_and_cleanup', 'passed': passed, 'proof_passed': proof_passed,
                  'ended_utc': c.stamp(), 'started_epoch': started, 'proof_deadline_epoch': proof_deadline,
                  'original_hard_end_epoch': c.HARD_END, 'prerequisites_validated': armed,
                  'cleanup_attempted': armed, 'cleanup_verified': cleanup_verified, 'error': error,
                  'source_commit': cp.get('source_commit') if cp else None,
                  'checkpoint_sha256': preparation.get('checkpoint_sha256') if preparation else None,
                  'serial': serial, 'checks': checks, 'native_assertions': native_summary,
                  'limitations': ['One custom-image minimum smoke; not old-consumer evolution, peak resources, performance, soak or fresh reproduction.',
                                  'Root must export/verify these receipts before teardown; this script performs no cloud operation.']}
        c.write(c.OUT / 'run-result.json', result)
        print(json.dumps(result), flush=True)
    return 0 if passed else 1


if __name__ == '__main__':
    raise SystemExit(main())
