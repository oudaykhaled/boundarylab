#!/usr/bin/env python3
"""Summarize actual physical-device observations without imputing missing values."""
from __future__ import annotations
import argparse
from collections import defaultdict
import hashlib
import json
import math
from pathlib import Path
import statistics

GROUP_KEYS = ('session', 'framework', 'treatment', 'operation', 'bytes',
              'process_state', 'cache_state', 'metric')


def analyze(rows: list[dict]) -> dict:
    if not rows:
        raise ValueError('no observations; an empty campaign is not a result')
    groups: dict[tuple, list[dict]] = defaultdict(list)
    identities: set[tuple] = set()
    for index, row in enumerate(rows, 1):
        if row.get('device_class') != 'physical':
            raise ValueError(f'row {index}: physical device evidence required')
        for key in GROUP_KEYS + ('trial', 'status'):
            if key not in row:
                raise ValueError(f'row {index}: missing {key}')
        if type(row['session']) is not int or row['session'] < 1 or type(row['trial']) is not int or row['trial'] < 1:
            raise ValueError(f'row {index}: invalid session/trial')
        if type(row['bytes']) is not int or not 0 <= row['bytes'] <= 134217728:
            raise ValueError(f'row {index}: invalid bytes; invalid workloads stay outside speed pools')
        for key, choices in [('framework', {'native','flutter'}), ('treatment', {'in_process','broker','no_work'}),
                             ('operation', {'generated-sha256-v1','regular-file-sha256-v1','none'}),
                             ('process_state', {'process_cold','resident'}), ('cache_state', {'prewarmed','uncontrolled'})]:
            if row[key] not in choices:
                raise ValueError(f'row {index}: invalid {key}')
        if not isinstance(row['metric'], str) or not row['metric']:
            raise ValueError(f'row {index}: invalid metric')
        identity = tuple(row[k] for k in GROUP_KEYS) + (row['trial'],)
        if identity in identities:
            raise ValueError(f'row {index}: duplicate observation identity')
        identities.add(identity)
        status = row['status']
        if status not in {'ok', 'failed', 'excluded'}:
            raise ValueError(f'row {index}: invalid status')
        value = row.get('value')
        if status == 'ok':
            if type(value) not in (int, float) or not math.isfinite(value) or value < 0:
                raise ValueError(f'row {index}: success requires a finite nonnegative value')
        else:
            if value is not None:
                raise ValueError(f'row {index}: failed/excluded values must be null')
            if not isinstance(row.get('reason'), str) or not row['reason']:
                raise ValueError(f'row {index}: missing failure/exclusion reason')
        groups[tuple(row[k] for k in GROUP_KEYS)].append(row)
    reports = []
    for key, values in sorted(groups.items()):
        good = sorted(row['value'] for row in values if row['status'] == 'ok')
        report = dict(zip(GROUP_KEYS, key))
        report.update(observed=len(values), passed=len(good),
                      failed=sum(row['status'] == 'failed' for row in values),
                      excluded=sum(row['status'] == 'excluded' for row in values),
                      median=statistics.median(good) if good else None,
                      p95_nearest_rank=good[math.ceil(.95 * len(good))-1] if len(good) >= 20 else None,
                      p95_note=None if len(good) >= 20 else 'insufficient observations (<20)',
                      exclusion_reasons=[row['reason'] for row in values if row['status'] != 'ok'])
        reports.append(report)
    return {'schema':'boundarylab-analysis-v1', 'observations':len(rows),
            'sessions':len({row['session'] for row in rows}),
            'claim':'descriptive per-session summaries; no causal speedup or population claim',
            'coverage_of_planned_campaign':'not established by this summary; reconcile plan and raw trial ledger',
            'groups':reports}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--input', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    try:
        if args.input.stat().st_size > 64 * 1024 * 1024:
            raise ValueError('input exceeds 64 MiB analysis cap; split by session')
        raw = args.input.read_bytes()
        rows = [json.loads(line) for line in raw.decode('utf-8').splitlines() if line.strip()]
        result = analyze(rows)
        result['input_sha256'] = hashlib.sha256(raw).hexdigest()
        with args.output.open('x', encoding='utf-8') as output:
            json.dump(result, output, indent=2, allow_nan=False)
            output.write('\n')
    except (OSError, ValueError, KeyError, TypeError) as error:
        parser.exit(2, f'analyze: {error}\n')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
