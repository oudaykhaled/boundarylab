#!/usr/bin/env python3
"""Apply this reviewed overlay only AFTER recording an unchanged baseline build/boot.
No source fetch, build, cloud mutation, Git command or privilege operation occurs here.
"""
import argparse
import hashlib
import json
from pathlib import Path
import shutil

p = argparse.ArgumentParser()
p.add_argument('aosp_root', type=Path)
p.add_argument('--baseline-receipt', required=True, type=Path,
               help='Existing operator-captured baseline build/boot evidence; this script does not certify it')
a = p.parse_args()
root = a.aosp_root.resolve()
source = Path(__file__).resolve().parents[1]
product = root / 'device/google/cuttlefish/vsoc_x86_64_only/phone/aosp_cf.mk'
board = root / 'device/google/cuttlefish/vsoc_x86_64_only/BoardConfig.mk'
destination = root / 'vendor/boundarylab'
if not (root / '.repo/manifest.xml').exists() or not product.is_file() or not board.is_file():
    raise SystemExit('Pinned Cuttlefish checkout structure missing')
if not a.baseline_receipt.is_file() or a.baseline_receipt.stat().st_size == 0:
    raise SystemExit('Nonempty actual baseline receipt required')
if destination.exists():
    raise SystemExit('Overlay destination already exists: inspect prior application instead of overwriting it')
changes = [(product, '$(call inherit-product, vendor/boundarylab/product.mk)'),
           (board, 'include vendor/boundarylab/BoardConfigBoundaryLab.mk')]
for file, line in changes:
    if line in file.read_text():
        raise SystemExit(f'Integration already present: {file}')
shutil.copytree(source, destination,
                ignore=shutil.ignore_patterns('evidence', '__pycache__', '*.pyc', '.gitignore'))
receipts = []
for file, line in changes:
    original = file.read_bytes()
    file.write_bytes(original + ('\n# BoundaryLab generated-only platform proof\n' + line + '\n').encode())
    receipts.append({'path': str(file.relative_to(root)), 'before_sha256': hashlib.sha256(original).hexdigest(),
                     'after_sha256': hashlib.sha256(file.read_bytes()).hexdigest()})
print(json.dumps({'stage': 'overlay_applied', 'files': receipts,
                  'baseline_receipt_sha256': hashlib.sha256(a.baseline_receipt.read_bytes()).hexdigest()}, indent=2))
