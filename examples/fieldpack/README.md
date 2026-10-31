# FieldPack: inspect an attachment handoff

An inspection recipient needs to know whether the note, readings and image they received match the sender's files. FieldPack creates a portable manifest of logical names, byte counts and SHA-256 digests. The recipient supplies the attachments separately and checks them locally. This can catch incomplete or changed handoffs before someone works from the wrong attachment.

**Available now:** this Python standard-library reference utility, project-owned files and executable checks. Separate native Android and Flutter clients use the Android Binder broker, and a desktop JVM recipient is implemented; see the [recorded mobile-to-desktop handoff](../../conformance/desktop/mobile-handoff.json). This Python utility executes in one process, so its demo does not exercise those implementations or establish their performance. The [root README](../../README.md#built-and-tested-on-a-real-linux-aosp-server) separately documents the Linux/AOSP source-build and native-service proof.

Requires Python 3.10+ on macOS or Linux/POSIX with no-follow, descriptor-relative file opening. Windows is unsupported. No installation, network service, account or upload is required. Run from the repository root:

```sh
python3 -m unittest discover -s examples/fieldpack -v
python3 examples/fieldpack/demo.py
```

The demo copies the actual fixture files to temporary sender and recipient directories. It checks a successful handoff, changes one byte, removes an attachment, and rejects an unsupported manifest. It asserts exit codes **0** (all match), **1** (a listed file does not match), and **2** (invalid manifest or operation error). Temporary files are removed when the demo finishes. There is no automatic device transfer or sync.

For your own files, place a bounded selection in a directory you control and use flat, portable lowercase names. Choose an output path outside that directory which does not already exist:

```sh
python3 examples/fieldpack/fieldpack.py create \
  --root /your/selected-attachments \
  --output /your/inspection.fieldpack.json inspection-note.txt readings.csv

# Supply the manifest and the selected files to the recipient yourself.
python3 examples/fieldpack/fieldpack.py verify \
  --root /recipient/received-attachments \
  --manifest /recipient/inspection.fieldpack.json
```

Names in the manifest must match the recipient filenames. Extra files are ignored: success covers **only the listed entries**, not the directory as a whole. Original file paths, Android URIs, timestamps, device identifiers and usernames are absent from the manifest; chosen logical names may still disclose information.

## Bounds and limitations

The [normative file profile](../../docs/FIELDPACK.md) fixes the schema and limits: at most 32 entries, 128 MiB per file and per pack, 64 KiB read chunks, and a 16 KiB manifest. Empty files are supported. Unknown fields, duplicate JSON keys, traversal names, reserved portable names, symlinks, hardlinks and nonregular files are rejected. A verifier does not read arbitrarily oversized recipient files merely to discover a size mismatch. Output creation is atomic and refuses to replace existing files; it is not an atomic snapshot of the selected input files.

Root and output directory ancestry must be trusted and controlled by the operator. Selected child files are opened relative to a pinned root descriptor without following symlinks. Before/after metadata checks detect some concurrent changes. They cannot prove an immutable or simultaneous snapshot, protect against every hostile same-user mutation, or guarantee a storage-read deadline. A digest describes the bytes observed during the read.

A matching manifest shows byte consistency. It does not authenticate the sender, establish legal evidence, detect malware, or protect against someone replacing both files and manifest. Cancellation is Ctrl-C (exit 130), with no persistent jobs or resume. Support for arbitrary Android/cloud providers is not implied by local fixture success.

## Fixture provenance

All three fixture files were authored for this repository on 2026-10-30 under its Apache-2.0 license. The text and CSV contain fictional training information. The 48×48 PNG is a programmatically drawn schematic, not a photograph of an inspection site or customer asset. The CLI hashes the files supplied by its caller; fixtures are used only by the walkthrough/tests.
