# FieldPack reference execution evidence

Executed 2026-10-30T12:39:25.034315+00:00 using Python 3.14.5 on Darwin arm64.

Scope: local Python reference, actual project-owned fixture files. No Android, Flutter, JVM host, Binder or AOSP execution occurred. Independent AI review: [review record](../../planning/reviews-v2/REFERENCE-REVIEW.md). No human or external adopter review is claimed.

## `python3 -m unittest discover -s examples/fieldpack -v`

Exit: 0.

```text
test_cli_exit_codes (test_fieldpack.FieldPackTests.test_cli_exit_codes) ... ok
test_count_and_total_caps (test_fieldpack.FieldPackTests.test_count_and_total_caps) ... ok
test_failed_create_and_interrupt_leave_no_manifest (test_fieldpack.FieldPackTests.test_failed_create_and_interrupt_leave_no_manifest) ... ok
test_known_vectors_and_deterministic_order (test_fieldpack.FieldPackTests.test_known_vectors_and_deterministic_order) ... ok
test_match_tamper_missing_and_extra_scope (test_fieldpack.FieldPackTests.test_match_tamper_missing_and_extra_scope) ... ok
test_name_policy (test_fieldpack.FieldPackTests.test_name_policy) ... ok
test_output_is_external_atomic_and_does_not_overwrite (test_fieldpack.FieldPackTests.test_output_is_external_atomic_and_does_not_overwrite) ... ok
test_read_error_and_mutation_close_descriptors (test_fieldpack.FieldPackTests.test_read_error_and_mutation_close_descriptors) ... ok
test_real_binary_multichunk_and_bounded_reads (test_fieldpack.FieldPackTests.test_real_binary_multichunk_and_bounded_reads) ... ok
test_replacement_after_read_is_detected (test_fieldpack.FieldPackTests.test_replacement_after_read_is_detected) ... ok
test_strict_manifest_negatives (test_fieldpack.FieldPackTests.test_strict_manifest_negatives) ... ok
test_symlink_hardlink_fifo_and_root_symlink (test_fieldpack.FieldPackTests.test_symlink_hardlink_fifo_and_root_symlink) ... ok

----------------------------------------------------------------------
Ran 12 tests in 0.015s

OK
```

## `python3 examples/fieldpack/demo.py`

Exit: 0.

```text
1. Create manifest from actual note, CSV and PNG files
{"result": "CREATED", "files": 3}
2. Verify separately copied recipient files
{"scope": "manifest_entries_only", "files": [{"name": "inspection-note.txt", "outcome": "MATCH"}, {"name": "inspection-schematic.png", "outcome": "MATCH"}, {"name": "sensor-readings.csv", "outcome": "MATCH"}]}
3. Change one byte without changing file size
{"scope": "manifest_entries_only", "files": [{"name": "inspection-note.txt", "outcome": "DIGEST_MISMATCH"}, {"name": "inspection-schematic.png", "outcome": "MATCH"}, {"name": "sensor-readings.csv", "outcome": "MATCH"}]}
4. Remove one recipient attachment
{"scope": "manifest_entries_only", "files": [{"name": "inspection-note.txt", "outcome": "DIGEST_MISMATCH"}, {"name": "inspection-schematic.png", "outcome": "MISSING"}, {"name": "sensor-readings.csv", "outcome": "MATCH"}]}
5. Reject an unsupported manifest version
{"error": "UNSUPPORTED_FORMAT"}
PASS: all expected outcomes and exit codes observed
```

## `python3 -O examples/fieldpack/demo.py`

Exit: 0.

```text
1. Create manifest from actual note, CSV and PNG files
{"result": "CREATED", "files": 3}
2. Verify separately copied recipient files
{"scope": "manifest_entries_only", "files": [{"name": "inspection-note.txt", "outcome": "MATCH"}, {"name": "inspection-schematic.png", "outcome": "MATCH"}, {"name": "sensor-readings.csv", "outcome": "MATCH"}]}
3. Change one byte without changing file size
{"scope": "manifest_entries_only", "files": [{"name": "inspection-note.txt", "outcome": "DIGEST_MISMATCH"}, {"name": "inspection-schematic.png", "outcome": "MATCH"}, {"name": "sensor-readings.csv", "outcome": "MATCH"}]}
4. Remove one recipient attachment
{"scope": "manifest_entries_only", "files": [{"name": "inspection-note.txt", "outcome": "DIGEST_MISMATCH"}, {"name": "inspection-schematic.png", "outcome": "MISSING"}, {"name": "sensor-readings.csv", "outcome": "MATCH"}]}
5. Reject an unsupported manifest version
{"error": "UNSUPPORTED_FORMAT"}
PASS: all expected outcomes and exit codes observed
```

## `python3 examples/fieldpack/fieldpack.py verify --root examples/fieldpack/fixtures --manifest examples/fieldpack/inspection.fieldpack.json`

Exit: 0.

```text
{"scope": "manifest_entries_only", "files": [{"name": "inspection-note.txt", "outcome": "MATCH"}, {"name": "inspection-schematic.png", "outcome": "MATCH"}, {"name": "sensor-readings.csv", "outcome": "MATCH"}]}
```

## Source identity

SHA-256 of the checked reference files (evidence document itself excluded):

```text
7c9678df8410d8412d94144dab12dd45f6775c064908c1d280d5ae2251b3074f  examples/fieldpack/README.md
f8dda9109e09d2ff482955d5d599661b90b98107f3ac8c03f63733eca3f38c48  examples/fieldpack/demo.py
9f08480e7e875933c641de9c64c36e380031e14adf61af3efecada94fb926f36  examples/fieldpack/fieldpack.py
d31091d8d9ec0dff457b8f679c1a604aa418fb72f43b50590eda3a5a8929608f  examples/fieldpack/fixtures/inspection-note.txt
dbad0f985de8012f2e79a6a730debfff95701fbdc2b7bf3d1ba58cb699cf1858  examples/fieldpack/fixtures/inspection-schematic.png
a092da6fa3e3e9af96ad3984b81f904b80b9cbcaade5bea44cca68834bebea10  examples/fieldpack/fixtures/sensor-readings.csv
98d36cf7c37f73aa670ff7fc7129f6bda7221925d27e26432e5195fcffcdf36f  examples/fieldpack/inspection.fieldpack.json
14f6bb595e3e6db1d27738a96b0b7c48c8a3530010ccb216ef414d02d114f74a  examples/fieldpack/test_fieldpack.py
```

Tests include controlled failures and mutation; these are injected local tests, not an account of a production incident. Timing above is not a benchmark. Re-run commands on your target; unsupported native capabilities remain planned.
