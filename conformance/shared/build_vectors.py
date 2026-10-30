#!/usr/bin/env python3
"""Regenerate portable contract inputs using only Python's standard library."""
import base64
import hashlib
import json
from pathlib import Path

HERE = Path(__file__).resolve().parent
MAX_BYTES = 134217728
EMPTY = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
ABC = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"


def encoded(value):
    return base64.b64encode(value).decode("ascii")


def compact(value):
    return (json.dumps(value, separators=(",", ":"), ensure_ascii=True) + "\n").encode()


def entry(name="abc.txt", size=3, digest=ABC):
    return {"name": name, "size": size, "sha256": digest}


def manifest(entries=None):
    return {"format": "fieldpack", "version": 1, "algorithm": "sha256",
            "files": entries if entries is not None else [entry(), entry("empty.bin", 0, EMPTY)]}


def write(name, data):
    (HERE / name).write_text(json.dumps(data, indent=2) + "\n")


def main():
    cases = []

    def add(case_id, data, valid=False, raw=None):
        case = {"id": case_id, "valid": valid, "rawBase64": encoded(raw if raw is not None else compact(data))}
        if valid:
            case["canonicalBase64"] = encoded(compact(data))
        cases.append(case)

    add("valid-empty-and-abc", manifest(), True)
    add("valid-single-empty", manifest([entry("empty.bin", 0, EMPTY)]), True)
    add("valid-alias-64", manifest([entry("a" * 64)]), True)
    add("valid-alias-63", manifest([entry("a" * 63)]), True)
    add("valid-31-entries", manifest([entry(f"f{i:02}", 0, EMPTY) for i in range(31)]), True)
    add("valid-32-entries", manifest([entry(f"f{i:02}", 0, EMPTY) for i in range(32)]), True)
    add("valid-file-limit", manifest([entry(size=MAX_BYTES)]), True)
    add("valid-file-limit-minus-one", manifest([entry(size=MAX_BYTES - 1)]), True)
    add("valid-total-limit", manifest([entry(size=MAX_BYTES - 1), entry("z", 1)]), True)
    add("valid-total-limit-minus-one", manifest([entry(size=MAX_BYTES - 2), entry("z", 1)]), True)
    add("valid-not-reserved", manifest([entry("com0"), entry("company.txt"), entry("lpt10"), entry("nulled")]), True)
    add("valid-object-key-order", manifest(), True, json.dumps({"files": [dict(reversed(list(e.items()))) for e in manifest()["files"]], "algorithm": "sha256", "version": 1, "format": "fieldpack"}, indent=2).encode())
    add("valid-escaped-ascii", manifest(), True, compact(manifest()).replace(b'abc.txt', b'\\u0061bc.txt'))
    base = compact(manifest())
    add("valid-byte-cap", manifest(), True, base + b" " * (16384 - len(base)))
    add("valid-byte-cap-minus-one", manifest(), True, base + b" " * (16383 - len(base)))
    add("invalid-byte-cap-plus-one", manifest(), raw=base + b" " * (16385 - len(base)))
    for field, values in {
        "version": [True, False, 1.0, "1", None, 0, 2, 9223372036854775808],
        "format": [True, None, "FieldPack", "other"],
        "algorithm": [True, None, "SHA256", "md5"],
        "files": [None, {}, "", [], [None]],
    }.items():
        for i, value in enumerate(values):
            data = manifest()
            data[field] = value
            add(f"invalid-{field}-{i}", data)
    for field in manifest():
        data = manifest()
        del data[field]
        add(f"invalid-missing-{field}", data)
    add("invalid-top-unknown-field", {**manifest(), "timestamp": 0})
    for i, value in enumerate([None, [], True, 1, "fieldpack"]):
        add(f"invalid-top-type-{i}", value)
    for field in entry():
        data = manifest([entry()])
        del data["files"][0][field]
        add(f"invalid-entry-missing-{field}", data)
    add("invalid-entry-unknown-field", manifest([{**entry(), "uri": "private"}]))
    for i, value in enumerate([True, False, -1, 1.0, 1.5, "3", None, MAX_BYTES + 1, 9223372036854775808]):
        add(f"invalid-size-{i}", manifest([entry(size=value)]))
    for i, value in enumerate(["A" * 64, "g" * 64, "0" * 63, "0" * 65, "", True, None, "0" * 63 + "\n"]):
        add(f"invalid-digest-{i}", manifest([entry(digest=value)]))
    names = ["", "a" * 65, ".", "..", "a.", "Upper.txt", "é.txt", "a/b", "a\\b", "a:b", "%61", "a b", "a\n", "_a", "-a", ".a", None, True]
    names += [f"{name}.txt" for name in ["con", "prn", "aux", "nul"] + [f"{p}{n}" for p in ("com", "lpt") for n in range(1, 10)]]
    for i, value in enumerate(names):
        add(f"invalid-name-{i}", manifest([entry(value)]))
    add("invalid-unsorted", manifest(list(reversed(manifest()["files"]))))
    add("invalid-duplicate-alias", manifest([entry(), entry()]))
    add("invalid-33-entries", manifest([entry(f"f{i:02}", 0, EMPTY) for i in range(33)]))
    add("invalid-total-limit-plus-one", manifest([entry(size=MAX_BYTES), entry("z", 1)]))
    for case_id, raw in {
        "duplicate-top": base.replace(b'"version":1', b'"version":1,"version":1'),
        "duplicate-entry": base.replace(b'"size":3', b'"size":3,"size":3'),
        "duplicate-escaped-key": base.replace(b'"version":1', b'"version":1,"\\u0076ersion":1'),
        "nan": base.replace(b'"size":3', b'"size":NaN'),
        "infinity": base.replace(b'"size":3', b'"size":Infinity'),
        "negative-infinity": base.replace(b'"size":3', b'"size":-Infinity'),
        "exponent-size": base.replace(b'"size":3', b'"size":3e0'),
        "bom": b"\xef\xbb\xbf" + base,
        "utf8": base.replace(b'abc.txt', b'\xff.txt'),
        "overlong-utf8": base.replace(b'abc.txt', b'\xc0\xaf.txt'),
        "lone-surrogate": base.replace(b'abc.txt', b'\\ud800.txt'),
        "trailing-content": base + b"{}",
        "trailing-comma": base[:-2] + b",}",
        "truncated": base[:-3],
        "deep-nesting": b"[" * 2000 + b"]" * 2000,
    }.items():
        add("invalid-" + case_id, None, raw=raw)
    write("manifest-v1.json", {"schemaVersion": 1, "contract": "docs/FIELDPACK.md", "cases": cases})

    files = []
    for case_id, kind, content, outcome in [
        ("match-abc", "regular", b"abc", "MATCH"),
        ("same-size-tamper", "regular", b"abd", "DIGEST_MISMATCH"),
        ("truncated", "regular", b"ab", "SIZE_MISMATCH"),
        ("grown", "regular", b"abcd", "SIZE_MISMATCH"),
        ("missing", "missing", b"", "MISSING"),
        ("directory", "directory", b"", "UNREADABLE_OR_UNSUPPORTED"),
        ("symlink", "symlink", b"abc", "UNREADABLE_OR_UNSUPPORTED"),
        ("hardlink", "hardlink", b"abc", "UNREADABLE_OR_UNSUPPORTED"),
        ("fifo", "fifo", b"", "UNREADABLE_OR_UNSUPPORTED"),
        ("empty", "regular", b"", "MATCH"),
    ]:
        expected = entry("subject.bin", 0, EMPTY) if case_id == "empty" else entry("subject.bin")
        files.append({"id": case_id, "entry": expected, "source": {"kind": kind, "contentBase64": encoded(content)}, "outcome": outcome})
    write("file-v1.json", {"schemaVersion": 1, "cases": files})

    digests = [{"id": "ascii-abc", "size": 3, "generator": {"kind": "base64", "data": encoded(b"abc")}, "sha256": ABC}]
    for seed in (0, 255, -1, 9223372036854775807):
        for size in (0, 1, 65535, 65536, 65537, MAX_BYTES):
            pattern = bytes((seed + i) & 255 for i in range(256)) * 256
            digest = hashlib.sha256()
            remaining = size
            while remaining:
                chunk = pattern[:min(remaining, len(pattern))]
                digest.update(chunk)
                remaining -= len(chunk)
            digests.append({"id": f"ramp-{seed}-{size}", "size": size, "generator": {"kind": "ramp", "seed": seed}, "sha256": digest.hexdigest()})
    write("digest-v1.json", {"schemaVersion": 1, "rampDefinition": "byte(i) = (seed + i) & 255; arithmetic interpreted modulo 256", "cases": digests})


if __name__ == "__main__":
    main()
