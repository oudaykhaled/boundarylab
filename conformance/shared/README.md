# Shared FieldPack v1 conformance corpus

The normative source is [FIELDPACK.md](../../docs/FIELDPACK.md). These JSON inputs
are shared between implementations; passing the Python runner proves only the
reference implementation on its recorded local filesystem.

## Portable inputs

- `manifest-v1.json`: `schemaVersion: 1`, then `cases`. Each case has `id`,
  `valid`, and `rawBase64`. Decode the base64 directly to bytes and pass those
  bytes to the production parser. Do not parse and reserialize first: that
  would erase duplicate keys and invalid encoding. Valid cases also carry
  `canonicalBase64`, the expected compact writer representation, with one LF.
  Object order and whitespace in input remain unrestricted. Limit-size entries
  use syntactically valid digests and exercise parser limits; they do not
  assert that their digests describe files of those sizes.
- `file-v1.json`: `cases` carry one expected manifest `entry`, a `source`
  recipe, and the exact verification `outcome`. Create each recipe in a new
  temporary root. `regular` writes `contentBase64`; `missing` creates nothing;
  `directory` creates a directory; `fifo` creates a FIFO without a writer;
  `symlink`/`hardlink` create a separate backing regular file with the supplied
  content and a link at the entry alias. Extra backing files are deliberately
  outside the listed set. Platforms unable to construct or safely inspect a
  required type must report the case unavailable, never passing.
- `digest-v1.json`: `cases` carry `id`, `size`, `generator`, and `sha256`.
  `base64` supplies literal `data`; `ramp` supplies an integer `seed`, with
  `byte(i) = (seed + i) & 255`. Parse seeds as signed 64-bit integers, not
  floating point. Ramp cases cover seeds 0, 255, -1 and `Long.MAX_VALUE`, with
  sizes 0, 1, 65,535, 65,536, 65,537 and 134,217,728. This corpus defines byte
  sequences for the engine tests; an adapter must independently prove its
  file ownership and transport behavior.

The manifest corpus covers cap−1/cap/cap+1 at the byte, alias, count, file-size,
and aggregate-size boundaries; boolean/fraction/exponent integer errors;
duplicate and escaped-duplicate keys; type errors; all reserved basename
families; invalid digest case/length; malformed UTF-8, BOM, and nesting.

## Reproduce reference evidence

```sh
python3 conformance/shared/build_vectors.py
python3 conformance/shared/run_reference.py --output docs/evidence/build/crossplatform-qa-reference.json
```

The final corpus has 127 manifest cases, 10 filesystem cases and 25 digest
cases. Together with the original 12 reference tests, this is 174 tests.

The runner executes the existing `examples/fieldpack/test_fieldpack.py` suite
plus one test per shared vector. File recipes use real local filesystem
objects. Digest cases write actual files in bounded chunks, execute the
reference writer, assert the independent SHA-256 result and exact compact
serialization, and observe the read request sizes (at most 65,536 bytes).
It uses the standard library and removes its temporary files after each case.

The JSON report records per-test status, counts, base commit, tested source
hashes, environment, and failures. `coverage: null` means no coverage tool was
run. Its base commit alone does not identify concurrently edited working-tree
sources; the SHA-256 map identifies the reference/corpus files tested.

This corpus does not replace adapter tests for input mutation, read-only
descriptor validation, shared cursors, queued/running cancellation, input
closure, process loss, provider blocking, signature/owner denial, or the
complete manual handoff. Those require their respective real boundaries.

## Other suite evidence

`run_reference.py --suite benchmarks --output PATH` executes the existing
benchmark analysis/schedule unit tests with the same JSON reporter. These
synthetic-analysis checks are not a latency campaign.

`run_suite.py` runs one bounded external command and captures a sanitized log
plus JSON under `docs/evidence/build/crossplatform-qa-NAME.*`. Supply `--junit`
glob(s) for Gradle's XML reporter or `--flutter` for `flutter test --machine`.
Only XML files generated during that invocation count; use `--rerun-tasks`
when fresh Gradle test execution is required. `--build-only` labels a compile
or static-analysis invocation without turning zero tests into a passing suite.
The timeout kills the spawned process group, and each attempt should use a
distinct `--name` to retain failed-run evidence. Source hashes before/after the
command expose concurrent working-tree changes; consumers should inspect those
changes before attributing results to a later source revision.
