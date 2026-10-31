#!/usr/bin/env python3
"""Run scoped library instrumentation on an explicitly selected, already prepared device.

Build/install preparation is intentionally external. This never provisions, installs,
resigns, uninstalls, roots, or wipes a device. See README.md for the two signer phases.
"""
import argparse
import datetime
import hashlib
import json
import pathlib
import re
import subprocess


def parse_result(stdout, exit_code, timed_out, expected_count, selection):
    """Strict instrumentation completion parsing; not proof of an Android run by itself."""
    events, current = [], {}
    for line in stdout.splitlines():
        status = re.match(r'INSTRUMENTATION_STATUS: ([^=]+)=(.*)', line)
        if status:
            current[status[1]] = status[2]
        code = re.match(r'INSTRUMENTATION_STATUS_CODE: (-?\d+)', line)
        if code:
            events.append(dict(current, status_code=int(code[1])))
            current = {}
    terminal = [e for e in events if e['status_code'] <= 0]
    starts = [e for e in events if e['status_code'] == 1]
    identities = lambda rows: [(e.get('class'), e.get('test')) for e in rows]
    declared = {int(e['numtests']) for e in events if e.get('numtests', '').isdigit()}
    selected = selection.split(',')
    valid_identity = lambda c, t: bool(c and t) and any(c == x or c + '#' + t == x for x in selected)
    complete = re.search(r'OK \((\d+) tests?\)', stdout)
    checks = {
        'transport_completed': exit_code == 0 and not timed_out,
        'runner_completed': bool(re.search(r'^INSTRUMENTATION_CODE: -1$', stdout, re.M)),
        'positive_expected_count': expected_count > 0,
        'declared_count_matches': declared == {expected_count},
        'summary_count_matches': bool(complete and int(complete[1]) == expected_count),
        'terminal_count_matches': len(terminal) == expected_count,
        'start_count_matches': len(starts) == expected_count,
        'unique_terminals': len(set(identities(terminal))) == len(terminal),
        'starts_match_terminals': set(identities(starts)) == set(identities(terminal)),
        'selected_identities': all(valid_identity(*x) for x in identities(terminal)),
        'all_required_passed': all(e['status_code'] == 0 for e in terminal),
        'no_failure_marker': not re.search(r'FAILURES!!!|INSTRUMENTATION_FAILED', stdout),
    }
    return dict(passed=all(checks.values()), checks=checks, events=events,
                tests_run=len(terminal), tests_passed=sum(e['status_code'] == 0 for e in terminal),
                tests_failed=sum(e['status_code'] in (-1, -2) for e in terminal),
                tests_skipped=sum(e['status_code'] in (-3, -4) for e in terminal))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--output', required=True, type=pathlib.Path)
    parser.add_argument('--phase', required=True, choices=['consumer', 'wrong-signer', 'broker', 'endpoint', 'denied-client'])
    parser.add_argument('--target-package', help='Override broker package for configured endpoint tests')
    args = parser.parse_args()
    external = 'dev.boundarylab.consumer.ExternalEndpointTest'
    defaults = {
        'consumer': ('dev.boundarylab.standalone', ','.join(external + '#' + name for name in [
            'sameSignerSeparateUidRunsRealSubmitAndStatus',
            'originalDefaultConstructorRunsAgainstDefaultInstalledBroker',
            'actualReadOnlyFileDescriptorProducesRecipientVerifiableManifest',
            'wrongExpectedPermissionIsDeniedAgainstRealInstalledService'])),
        'wrong-signer': ('dev.boundarylab.standalone', external + '#installedWrongSignerBrokerIsDeniedAndDescriptorClosed'),
        'broker': ('dev.boundarylab.broker', ','.join('dev.boundarylab.broker.' + name for name in [
            'BrokerEndpointTest', 'SdkOwnershipTest', 'SdkLifecycleTest', 'CrossOwnerTest'])),
        'endpoint': ('dev.boundarylab.broker', 'dev.boundarylab.broker.BrokerEndpointTest'),
        'denied-client': ('dev.boundarylab.deniedclient', 'dev.boundarylab.deniedclient.SignatureDenialTest'),
    }
    package, selection = defaults[args.phase]
    if args.target_package:
        if args.phase not in ('broker', 'endpoint'):
            parser.error('--target-package applies only to broker/endpoint phases')
        if not re.fullmatch(r'[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z_][A-Za-z0-9_]*)+', args.target_package):
            parser.error('--target-package must be a qualified Android package name')
    package = args.target_package or package
    component = package + '.test/androidx.test.runner.AndroidJUnitRunner'
    output = args.output
    output.mkdir(parents=True, exist_ok=True)
    commands = []

    def run(command, timeout=30):
        argv = ['adb', '-s', args.serial, *command]
        record = dict(argv=argv, timeout_seconds=timeout, exit_code=None, stdout='', stderr='', timed_out=False)
        try:
            result = subprocess.run(argv, text=True, capture_output=True, timeout=timeout)
            record.update(exit_code=result.returncode, stdout=result.stdout, stderr=result.stderr)
        except subprocess.TimeoutExpired as error:
            def text(value):
                return value.decode(errors='replace') if isinstance(value, bytes) else (value or '')
            record.update(timed_out=True, stdout=text(error.stdout), stderr=text(error.stderr), error='command timeout')
        except OSError as error:
            record['error'] = str(error)
        commands.append(record)
        return record

    started = datetime.datetime.now(datetime.timezone.utc).isoformat()
    state = run(['get-state'])
    ready = state['exit_code'] == 0 and state['stdout'].strip() == 'device'
    setup_ok = ready
    if ready:
        for command in [['logcat', '-c'], ['shell', 'am', 'force-stop', package]]:
            if run(command)['exit_code'] != 0:
                setup_ok = False
                break
    command = ['shell', 'am', 'instrument', '-w', '-r', '-e', 'class', selection]
    if args.phase == 'wrong-signer':
        command += ['-e', 'expectedWrongSigner', 'true']
    result = run(command + [component], timeout=300) if setup_ok else dict(stdout='', stderr='setup failed', exit_code=None, timed_out=False)
    if setup_ok and (result['timed_out'] or result['exit_code'] != 0):
        # Host adb termination alone does not guarantee remote instrumentation termination.
        run(['shell', 'am', 'force-stop', package], timeout=30)
    logs = run(['logcat', '-d', '-v', 'threadtime', 'BoundaryLabConsumer:I',
                'ExternalEndpointQA:I', 'TestRunner:I', 'AndroidRuntime:E', '*:S']) if ready else dict(stdout='', stderr='device unavailable')
    stdout = result['stdout'] + result['stderr']
    expected = {'consumer': 4, 'wrong-signer': 1, 'broker': 24, 'endpoint': 12, 'denied-client': 1}[args.phase]
    summary = parse_result(stdout, result['exit_code'], result['timed_out'], expected, selection)
    summary['passed'] = summary['passed'] and setup_ok and all(c['exit_code'] == 0 and not c['timed_out'] for c in commands)
    receipt = dict(started=started, finished=datetime.datetime.now(datetime.timezone.utc).isoformat(),
                   phase=args.phase, commands=commands, **summary,
                   output_sha256=hashlib.sha256(stdout.encode()).hexdigest())
    (output / (args.phase + '.log')).write_text(stdout)
    (output / (args.phase + '-logcat.log')).write_text(logs['stdout'] + logs['stderr'])
    (output / (args.phase + '.json')).write_text(json.dumps(receipt, indent=2) + '\n')
    print(stdout, end='')
    print(json.dumps({k: receipt[k] for k in ['phase', 'passed', 'tests_run', 'tests_passed', 'tests_failed', 'tests_skipped']}))
    raise SystemExit(0 if summary['passed'] else 1)


if __name__ == '__main__':
    main()
