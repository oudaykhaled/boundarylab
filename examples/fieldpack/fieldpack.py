#!/usr/bin/env python3
"""FieldPack v1 reference: bounded local file integrity, Python standard library only."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import stat
import sys
import tempfile

MAX_FILES = 32
MAX_BYTES = 128 * 1024 * 1024  # Per file AND sum of manifest entries.
CHUNK = 64 * 1024
MAX_MANIFEST = 16 * 1024
NAME = re.compile(r"[a-z0-9][a-z0-9._-]{0,63}\Z", re.ASCII)
DIGEST = re.compile(r"[0-9a-f]{64}\Z", re.ASCII)
RESERVED = {"con", "prn", "aux", "nul"} | {
    f"{prefix}{n}" for prefix in ("com", "lpt") for n in range(1, 10)
}


class PackError(Exception):
    """Stable, path-free diagnostic suitable for example output."""


class SizeMismatch(PackError):
    pass


def check_name(name):
    if (not isinstance(name, str) or not NAME.fullmatch(name)
            or name.endswith(".") or name.split(".", 1)[0] in RESERVED):
        raise PackError("INVALID_NAME")
    return name


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise PackError("DUPLICATE_JSON_KEY")
        result[key] = value
    return result


def invalid_constant(_):
    raise PackError("INVALID_JSON_NUMBER")


def validate_manifest(raw):
    if len(raw) > MAX_MANIFEST:
        raise PackError("MANIFEST_TOO_LARGE")
    try:
        data = json.loads(raw.decode("utf-8"), object_pairs_hook=unique_object,
                          parse_constant=invalid_constant)
    except (UnicodeError, ValueError, RecursionError) as exc:
        raise PackError("INVALID_JSON") from exc
    if not isinstance(data, dict) or set(data) != {"format", "version", "algorithm", "files"}:
        raise PackError("INVALID_MANIFEST_FIELDS")
    if (data["format"] != "fieldpack" or type(data["version"]) is not int
            or data["version"] != 1 or data["algorithm"] != "sha256"):
        raise PackError("UNSUPPORTED_FORMAT")
    entries = data["files"]
    if not isinstance(entries, list) or not 1 <= len(entries) <= MAX_FILES:
        raise PackError("INVALID_FILE_COUNT")
    names, total = [], 0
    for entry in entries:
        if not isinstance(entry, dict) or set(entry) != {"name", "size", "sha256"}:
            raise PackError("INVALID_ENTRY_FIELDS")
        names.append(check_name(entry["name"]))
        size = entry["size"]
        if type(size) is not int or not 0 <= size <= MAX_BYTES:
            raise PackError("INVALID_SIZE")
        total += size
        if not isinstance(entry["sha256"], str) or not DIGEST.fullmatch(entry["sha256"]):
            raise PackError("INVALID_DIGEST")
    if names != sorted(set(names)):
        raise PackError("DUPLICATE_OR_UNSORTED_NAMES")
    if total > MAX_BYTES:
        raise PackError("PACK_TOO_LARGE")
    return data


def open_root(root):
    if os.name != "posix" or not hasattr(os, "O_NOFOLLOW"):
        raise PackError("UNSUPPORTED_PLATFORM")
    return os.open(root, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW)


def identity(info):
    return (info.st_dev, info.st_ino, info.st_size, info.st_mtime_ns,
            info.st_ctime_ns, info.st_nlink)


def hash_file(root_fd, name, budget=MAX_BYTES, expected_size=None):
    """Pin a flat, single-link regular file; never follow a selected symlink."""
    check_name(name)
    fd = os.open(name, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK, dir_fd=root_fd)
    try:
        before = os.fstat(fd)
        if not stat.S_ISREG(before.st_mode) or before.st_nlink != 1:
            raise PackError("UNSUPPORTED_SOURCE")
        if expected_size is not None and before.st_size != expected_size:
            raise SizeMismatch("SIZE_MISMATCH")
        if before.st_size > budget:
            raise PackError("INPUT_TOO_LARGE")
        os.lseek(fd, 0, os.SEEK_SET)
        digest, size = hashlib.sha256(), 0
        while True:
            part = os.read(fd, min(CHUNK, budget - size + 1))
            if not part:
                break
            size += len(part)
            if size > budget:
                raise PackError("INPUT_TOO_LARGE")
            digest.update(part)
        after = os.fstat(fd)
        if identity(before) != identity(after) or size != before.st_size:
            raise PackError("INPUT_CHANGED")
        named = os.stat(name, dir_fd=root_fd, follow_symlinks=False)
        if identity(named) != identity(after) or not stat.S_ISREG(named.st_mode):
            raise PackError("INPUT_CHANGED")
        return {"name": name, "size": size, "sha256": digest.hexdigest()}
    finally:
        os.close(fd)


def create(root, names):
    if not 1 <= len(names) <= MAX_FILES or len(names) != len(set(names)):
        raise PackError("INVALID_FILE_COUNT_OR_DUPLICATE")
    for name in names:
        check_name(name)
    root_fd = open_root(root)
    try:
        entries, remaining = [], MAX_BYTES
        for name in sorted(names):
            entry = hash_file(root_fd, name, remaining)
            remaining -= entry["size"]
            entries.append(entry)
    finally:
        os.close(root_fd)
    data = {"format": "fieldpack", "version": 1, "algorithm": "sha256", "files": entries}
    raw = (json.dumps(data, separators=(",", ":"), ensure_ascii=True) + "\n").encode("utf-8")
    validate_manifest(raw)
    return raw


def write_manifest(root, output, raw):
    # Caller controls/trusts the directory ancestry; selected children are untrusted.
    root_path = os.path.realpath(root)
    target = Path(os.path.abspath(output))
    parent = os.path.realpath(target.parent)
    if os.path.commonpath((root_path, parent)) == root_path:
        raise PackError("OUTPUT_MUST_BE_OUTSIDE_ROOT")
    temporary = None
    try:
        with tempfile.NamedTemporaryFile(dir=parent, prefix=".fieldpack-", delete=False) as handle:
            temporary = handle.name
            handle.write(raw)
            handle.flush()
            os.fsync(handle.fileno())
        # Atomic create without replacing any existing file or symlink.
        os.link(temporary, os.path.join(parent, target.name))
    finally:
        if temporary is not None:
            os.unlink(temporary)


def load_manifest(path):
    fd = os.open(path, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK)
    try:
        if not stat.S_ISREG(os.fstat(fd).st_mode):
            raise PackError("UNSUPPORTED_MANIFEST_SOURCE")
        raw = b""
        while len(raw) <= MAX_MANIFEST:
            part = os.read(fd, MAX_MANIFEST + 1 - len(raw))
            if not part:
                break
            raw += part
        return validate_manifest(raw)
    finally:
        os.close(fd)


def verify(root, data):
    # Public API validates too; do not trust an in-memory caller's schema.
    data = validate_manifest(json.dumps(data, ensure_ascii=True).encode("utf-8"))
    root_fd = open_root(root)
    results = []
    try:
        for expected in data["files"]:
            try:
                actual = hash_file(root_fd, expected["name"], expected["size"], expected["size"])
                if actual["size"] != expected["size"]:
                    outcome = "SIZE_MISMATCH"
                elif actual["sha256"] != expected["sha256"]:
                    outcome = "DIGEST_MISMATCH"
                else:
                    outcome = "MATCH"
            except FileNotFoundError:
                outcome = "MISSING"
            except SizeMismatch:
                outcome = "SIZE_MISMATCH"
            except (OSError, PackError):
                outcome = "UNREADABLE_OR_UNSUPPORTED"
            results.append({"name": expected["name"], "outcome": outcome})
    finally:
        os.close(root_fd)
    return results


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    make = commands.add_parser("create")
    make.add_argument("--root", required=True)
    make.add_argument("--output", required=True)
    make.add_argument("names", nargs="+")
    check = commands.add_parser("verify")
    check.add_argument("--root", required=True)
    check.add_argument("--manifest", required=True)
    args = parser.parse_args(argv)
    try:
        if args.command == "create":
            raw = create(args.root, args.names)
            write_manifest(args.root, args.output, raw)
            print(json.dumps({"result": "CREATED", "files": len(args.names)}))
            return 0
        results = verify(args.root, load_manifest(args.manifest))
        print(json.dumps({"scope": "manifest_entries_only", "files": results}))
        return int(any(item["outcome"] != "MATCH" for item in results))
    except (PackError, OSError) as exc:
        print(json.dumps({"error": str(exc) if isinstance(exc, PackError) else "IO_ERROR"}))
        return 2
    except KeyboardInterrupt:
        print(json.dumps({"error": "INTERRUPTED"}))
        return 130


if __name__ == "__main__":
    sys.exit(main())
