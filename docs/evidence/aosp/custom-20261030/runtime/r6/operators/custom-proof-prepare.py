#!/usr/bin/env python3
"""Root runs only after baseline export is locally verified and custom builds pass.
Preparation does not launch CVD, change AppArmor, install a daemon, or access cloud.
Checkpoint schema is documented in custom-proof-common.py. Never overwrite a run.
"""
import importlib.util
import json
import os
from pathlib import Path
import pwd
import shutil
import time

spec = importlib.util.spec_from_file_location('custom_proof_common', Path(__file__).with_name('custom-proof-common.py'))
c = importlib.util.module_from_spec(spec)
spec.loader.exec_module(c)


def main():
    c.require(os.geteuid() == 0, 'root operator required')
    c.require(not c.OUT.exists(), 'proof output already exists; preserve and inspect it')
    c.OUT.mkdir()
    account = pwd.getpwnam('aosp')
    os.chown(c.OUT, account.pw_uid, account.pw_gid)
    sequence = 0
    prepared = False
    error = None
    value = {}

    def command(argv, empty_ps_allowed=False):
        nonlocal sequence
        sequence += 1
        name = 'prepare-' + str(sequence).zfill(2)
        receipt = c.stage(name, argv, min(120, c.HARD_END - time.time()))
        text = (c.OUT / (name + '.stdout.log')).read_text().strip()
        no_user_processes = (empty_ps_allowed and receipt['exit_code'] == 1 and not receipt['timed_out'] and
                             not text and not (c.OUT / (name + '.stderr.log')).read_text().strip())
        c.require(c.success(receipt) or no_user_processes, 'preparation command failed: ' + name)
        return text

    try:
        cp = c.gates()
        c.require(c.HOME_DIR.resolve() == c.HOME_DIR and c.HOME_DIR.stat().st_uid == account.pw_uid,
                  'unexpected attempt HOME directory')
        c.require(command(c.PREFIX + ['git', '-C', str(c.SRC / '.repo/manifests'), 'rev-parse', 'HEAD']) == c.MANIFEST,
                  'pinned manifest checkout differs')
        c.require(not (c.SRC / '.repo/local_manifests').exists(), 'unexpected local manifest')
        c.require(shutil.disk_usage(c.BASE).free >= 40 * 1024**3, 'below original40GiB disk reserve')
        for name in ('launch_cvd', 'stop_cvd', 'adb', 'sepolicy-analyze'):
            path = c.HOST / 'bin' / name
            c.require(path.is_file() and os.access(path, os.X_OK) and path.resolve().is_relative_to(c.HOST.resolve()),
                      'missing/outside matching host tool ' + name)
        crosvm = (c.HOST / 'bin/x86_64-linux-gnu/crosvm').resolve(strict=True)
        c.require(crosvm.is_relative_to(c.HOST.resolve()) and crosvm.open('rb').read(4) == b'\x7fELF',
                  'actual matching x86 crosvm ELF missing')
        expected_profile = ('abi <abi/4.0>,\ninclude <tunables/global>\nprofile boundarylab-crosvm ' + str(crosvm) +
                            ' flags=(unconfined) {\n  userns,\n}\n')
        profile = c.PROFILE.read_text()
        baseline_preparation = json.loads((c.BASELINE / 'boot-preparation.json').read_text())
        c.require(profile == expected_profile == (c.BASELINE / 'apparmor-profile-applied.txt').read_text() and
                  c.digest(c.PROFILE) == baseline_preparation['apparmor_profile_sha256'],
                  'AppArmor exact source-ELF profile changed; do not repair automatically')
        for key in ('kernel.apparmor_restrict_unprivileged_userns', 'kernel.unprivileged_userns_clone'):
            c.require(command(['sysctl', '-n', key]) == '1', key + ' must remain1')
        processes = command(['ps', '-u', 'aosp', '-o', 'pid=,comm=,args='], empty_ps_allowed=True)
        cvd = [row for row in c.cvd_rows(processes) if row.split(None, 2)[1].strip('[]') != 'adb']
        c.require(not cvd, 'prior CVD/host-tool process still alive')
        c.require(not any(row.split(None, 2)[1].startswith(('ninja', 'soong_', 'clang'))
                          for row in processes.splitlines() if len(row.split(None, 2)) >= 2), 'build process still alive')
        binaries = {}
        for name in c.BINS:
            path = c.PRODUCT / 'system_ext/bin' / name
            c.require(path.is_file() and not path.is_symlink() and os.access(path, os.X_OK), 'missing image-installed binary ' + name)
            binaries[name] = {'host_path': str(path), 'device_path': '/system_ext/bin/' + name,
                              'sha256': c.digest(path), 'bytes': path.stat().st_size}
        test = c.PRODUCT / c.TEST_DEVICE.lstrip('/')
        c.require(test.is_file() and not test.is_symlink() and os.access(test, os.X_OK),
                  'actual expected source-built native test output missing')
        binaries['boundarylab_engine_test'] = {'host_path': str(test), 'device_path': c.TEST_DEVICE,
                                               'sha256': c.digest(test), 'bytes': test.stat().st_size}
        artifacts = []
        for path in sorted(c.PRODUCT.glob('*.img')):
            c.require(path.is_file() and not path.is_symlink(), 'unexpected image symlink/type')
            artifacts.append({'path': str(path), 'sha256': c.digest(path), 'bytes': path.stat().st_size})
        c.require(any(a['path'] == str(c.PRODUCT / 'super.img') for a in artifacts), 'custom super.img missing')
        for name in ('launch_cvd', 'stop_cvd', 'adb', 'sepolicy-analyze'):
            path = (c.HOST / 'bin' / name).resolve(strict=True)
            artifacts.append({'path': str(path), 'sha256': c.digest(path), 'bytes': path.stat().st_size})
        artifacts.append({'path': str(crosvm), 'sha256': c.digest(crosvm), 'bytes': crosvm.stat().st_size})
        # Only previously exported attempt-owned baseline state may be removed.
        links = {'.cuttlefish_config.json': 'assembly/cuttlefish_config.json', 'cuttlefish_assembly': 'assembly',
                 'cuttlefish_runtime': 'instances/cvd-1', 'cuttlefish_runtime.1': 'instances/cvd-1'}
        for name, relative in links.items():
            path = c.HOME_DIR / name
            c.require((path.is_symlink() and path.readlink() == c.HOME_DIR / 'cuttlefish' / relative) or
                      (not path.is_symlink() and not path.exists()), 'unexpected CVD HOME link ' + name)
        state = c.HOME_DIR / 'cuttlefish'
        c.require(not state.is_symlink() and (not state.exists() or state.is_dir()), 'unsafe CVD state root')
        c.require(time.time() < c.NO_NEW_START, 'no-new-start gate reached before preparation mutation')
        command(c.PREFIX + [str(c.HOST / 'bin/adb'), 'kill-server'])
        for name in links:
            path = c.HOME_DIR / name
            if path.is_symlink():
                command(c.PREFIX + ['rm', '--', str(path)])
        if state.exists():
            command(c.PREFIX + ['rm', '-rf', '--', str(state)])
        c.require(not state.exists() and c.PROFILE.read_text() == profile, 'state cleanup or profile stability failed')
        source_snapshot = c.OUT / 'source-checkpoint.json'
        with source_snapshot.open('xb') as stream:
            stream.write(c.CHECKPOINT.read_bytes())
        value = {'kind': 'custom_source_boot_prepared', 'prepared': True, 'utc': c.stamp(),
                 'checkpoint_sha256': c.digest(c.CHECKPOINT), 'source_commit': cp['source_commit'],
                 'target': c.TARGET, 'host_out': str(c.HOST), 'product_out': str(c.PRODUCT),
                 'apparmor_profile_sha256': c.digest(c.PROFILE), 'crosvm_elf': str(crosvm),
                 'artifacts': artifacts, 'binaries': binaries,
                 'operator_scripts_sha256': {p.name: c.digest(p) for p in sorted(Path(__file__).parent.glob('custom-proof-*'))
                                             if p.is_file() and p.suffix in ('.py', '.sh')},
                 'baseline_export_reference': cp['baseline_local_export'], 'guest_boot_proven': False}
        c.write(c.OUT / 'preparation.json', value)
        prepared = True
    except BaseException as exc:
        error = repr(exc)
    finally:
        c.write(c.OUT / 'preparation-result.json', {'prepared': prepared, 'ended_utc': c.stamp(), 'error': error})
    print(json.dumps({'prepared': prepared, 'error': error}), flush=True)
    return 0 if prepared else 1


if __name__ == '__main__':
    raise SystemExit(main())
