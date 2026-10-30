#!/usr/bin/env python3
"""Create a deterministic balanced schedule; never execute or invent observations."""
from __future__ import annotations
import argparse
import hashlib
import itertools
import json
from pathlib import Path
import random


def schedule(plan: dict) -> dict:
    if plan.get('schema') != 'boundarylab-experiment-v1':
        raise ValueError('unsupported experiment schema')
    for key in ('sessions', 'interactions_per_combination_per_session', 'random_seed'):
        if type(plan.get(key)) is not int or plan[key] < 1:
            raise ValueError(f'{key} must be a positive integer')
    combinations = list(itertools.product(plan['frameworks'], plan['treatments'], plan['startup_states']))
    if not combinations or len(set(combinations)) != len(combinations):
        raise ValueError('empty or duplicate combinations')
    rng = random.Random(plan['random_seed'])
    rows = []
    for session in range(1, plan['sessions'] + 1):
        for block in range(1, plan['interactions_per_combination_per_session'] + 1):
            order = combinations.copy()
            rng.shuffle(order)
            for framework, treatment, state in order:
                rows.append({'session': session, 'block': block, 'order': len(rows) + 1,
                             'framework': framework, 'treatment': treatment, 'process_state': state})
    canonical = json.dumps(plan, sort_keys=True, separators=(',', ':')).encode()
    return {'schema': 'boundarylab-schedule-v1', 'executed': False,
            'plan_sha256': hashlib.sha256(canonical).hexdigest(), 'interactions': rows,
            'launches': launch_schedule(plan, rng)}


def launch_schedule(plan: dict, rng: random.Random) -> list[dict]:
    rows = []
    combinations = list(itertools.product(plan['frameworks'], plan['startup_states']))
    for session in range(1, plan['sessions'] + 1):
        for block in range(1, plan['launches_per_framework_per_state_per_session'] + 1):
            order = combinations.copy()
            rng.shuffle(order)
            for framework, state in order:
                rows.append({'session': session, 'block': block, 'order': len(rows) + 1,
                             'framework': framework, 'treatment': 'no_work', 'process_state': state})
    return rows


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--plan', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    try:
        result = schedule(json.loads(args.plan.read_text()))
        with args.output.open('x', encoding='utf-8') as output:
            json.dump(result, output, indent=2, allow_nan=False)
            output.write('\n')
    except (OSError, ValueError, KeyError, TypeError) as error:
        parser.exit(2, f'schedule: {error}\n')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
