#!/usr/bin/env python3
"""Bounded, append-only AOSP attempt evidence. No cloud/device discovery or shell."""
import argparse
import datetime as dt
import hashlib
import json
import os
from pathlib import Path
import re
import selectors
import signal
import stat
import subprocess
import sys
import time
import uuid

SCOPES = ("resource-preflight", "prebuilt-baseline", "source-baseline", "custom-image", "collector-self-test")
PROBES = ("boot", "authorized", "denied", "malformed", "over-cap", "unsupported-file", "cancel", "restart", "supervision", "compatibility", "soak", "experiment", "reproduction")


def now():
    return dt.datetime.now(dt.timezone.utc).isoformat()


def digest(path):
    h = hashlib.sha256()
    with path.open("rb") as f:
        for block in iter(lambda: f.read(1024 * 1024), b""):
            h.update(block)
    return h.hexdigest()


def write_new(path, value):
    with path.open("x", encoding="utf-8") as f:
        json.dump(value, f, indent=2, ensure_ascii=False, allow_nan=False)
        f.write("\n")
        f.flush()
        os.fsync(f.fileno())


def clean(text, redactions):
    for value in sorted(set(redactions + [str(Path.home()), str(Path.cwd())]), key=len, reverse=True):
        if value and value != "/":
            text = text.replace(value, "<redacted>")
    return text


def clean_log(text, redactions, tail=False):
    """Also hide a sensitive literal split at the retained/snapshot boundary."""
    text = clean(text, redactions)
    for value in redactions + [str(Path.home()), str(Path.cwd())]:
        if tail:
            for size in range(min(len(value) - 1, len(text)), 0, -1):
                if text.startswith(value[-size:]):
                    text = "<redacted-suffix>" + text[size:]
                    break
        for size in range(min(len(value) - 1, len(text)), 0, -1):
            if text.endswith(value[:size]):
                text = text[:-size] + "<redacted-prefix>"
                break
    return text


def snapshot(path, data, redactions, tail=False):
    pending = path.with_suffix(".partial")
    with pending.open("w", encoding="utf-8") as f:
        f.write(clean_log(data.decode("utf-8", errors="replace"), redactions, tail=tail))
        f.flush()
        os.fsync(f.fileno())
    os.replace(pending, path)


def identifier(label):
    if not label or len(label) > 80 or any(c not in "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_." for c in label):
        raise ValueError("Labels must be 1–80 ASCII letters/digits/dot/dash/underscore")
    return label + "-" + uuid.uuid4().hex


def bundle(args):
    base = Path(args.bundle).resolve()
    metadata = json.loads((base / "attempt.json").read_text())
    if metadata.get("schema_version") != 1:
        raise ValueError("Unsupported attempt schema")
    return base


def init(args):
    base = Path(args.bundle)
    base.mkdir(parents=True, exist_ok=False)
    (base / "events").mkdir()
    (base / "artifacts").mkdir()
    write_new(base / "attempt.json", {
        "schema_version": 1, "event_type": "attempt", "created_utc": now(),
        "attempt_id": args.attempt_id, "source_commit": args.source_commit,
        "build_target": args.build_target, "device_class": "virtual-cuttlefish",
        "source_manifest": "not-yet-captured", "initial_probe_status": {p: "not-run" for p in PROBES},
        "limitations": ["Collection does not approve P01–P09 acceptance.", "Prebuilt boot is not an unchanged source build.", "No physical-device performance claim."],
    })
    print(json.dumps({"initialized": True, "attempt_id": args.attempt_id}))
    return 0


def collect(args):
    base = bundle(args)
    argv = args.command[1:] if args.command[:1] == ["--"] else args.command
    if not argv:
        raise ValueError("A command after -- is required")
    event_id = identifier(args.label)
    started = now()
    start = time.monotonic()
    log_paths = {key: base / "artifacts" / (event_id + "." + key + ".log") for key in ("stdout", "stderr")}
    tail_paths = {key: base / "artifacts" / (event_id + "." + key + ".tail.log") for key in ("stdout", "stderr")}
    for path in [*log_paths.values(), *tail_paths.values()]:
        with path.open("x", encoding="utf-8"):
            pass
    write_new(base / "events" / (event_id + ".started.json"), {
        "schema_version": 1, "event_type": "command-start", "id": event_id,
        "scope": args.scope, "label": args.label, "started_utc": started,
        "command": [clean(x, args.redact) for x in argv], "timeout_seconds": args.timeout,
        "partial_log_paths": [str(p.relative_to(base)) for p in [*log_paths.values(), *tail_paths.values()]],
        "status": "started; only a corresponding final command receipt establishes completion",
    })
    streams = {k: {"hash": hashlib.sha256(), "bytes": 0, "data": bytearray(), "tail": bytearray()} for k in ("stdout", "stderr")}
    timed_out = False
    launch_error = None
    code = None
    proc = None
    try:
        proc = subprocess.Popen(argv, stdout=subprocess.PIPE, stderr=subprocess.PIPE, start_new_session=True)
        sel = selectors.DefaultSelector()
        for key in streams:
            pipe = getattr(proc, key)
            os.set_blocking(pipe.fileno(), False)
            sel.register(pipe, selectors.EVENT_READ, key)
        try:
            while sel.get_map():
                if time.monotonic() - start > args.timeout:
                    timed_out = True
                    break
                for selected, _ in sel.select(timeout=0.1):
                    data = os.read(selected.fd, 65536)
                    if not data:
                        sel.unregister(selected.fileobj)
                        selected.fileobj.close()
                        continue
                    stream = streams[selected.data]
                    stream["hash"].update(data)
                    stream["bytes"] += len(data)
                    left = max(0, args.max_log_bytes - len(stream["data"]))
                    stream["data"].extend(data[:left])
                    stream["tail"].extend(data)
                    del stream["tail"][:-args.max_tail_bytes]
                    if left:
                        # Atomic bounded snapshots allow export while a long build
                        # runs; the final receipt later seals each log's hash.
                        snapshot(log_paths[selected.data], stream["data"], args.redact)
                    snapshot(tail_paths[selected.data], stream["tail"], args.redact, tail=True)
            remaining = max(0.01, args.timeout - (time.monotonic() - start))
            if not timed_out:
                try:
                    code = proc.wait(timeout=remaining)
                except subprocess.TimeoutExpired:
                    timed_out = True
        finally:
            # Kill the owned process group even when its leader exited: descendants
            # must not survive a bounded collector invocation.
            try:
                os.killpg(proc.pid, signal.SIGKILL)
            except ProcessLookupError:
                pass
            code = proc.wait(timeout=5)
            for selected in list(sel.get_map().values()):
                selected.fileobj.close()
            sel.close()
    except OSError as exc:
        launch_error = clean(str(exc), args.redact)
    output = {}
    for key, stream in streams.items():
        path = log_paths[key]
        snapshot(path, stream["data"], args.redact)
        tail_path = tail_paths[key]
        snapshot(tail_path, stream["tail"], args.redact, tail=True)
        output[key] = {"path": str(path.relative_to(base)), "sha256": digest(path),
                       "captured_stream_sha256": stream["hash"].hexdigest(), "observed_bytes": stream["bytes"],
                       "retained_input_bytes": len(stream["data"]), "truncated": stream["bytes"] > len(stream["data"]),
                       "stream_complete": not timed_out and launch_error is None,
                       "tail": {"path": str(tail_path.relative_to(base)), "sha256": digest(tail_path),
                                "retained_input_bytes": len(stream["tail"]),
                                "offset_bytes": stream["bytes"] - len(stream["tail"]),
                                "truncated": stream["bytes"] > len(stream["tail"])}}
    command_ok = code == args.expect_exit and not timed_out and launch_error is None
    assertion = None
    assertion_error = None
    if args.probe_json:
        try:
            if output["stdout"]["truncated"] or not command_ok:
                raise ValueError("Incomplete output or unexpected command outcome")
            assertion = json.loads(streams["stdout"]["data"].decode("utf-8"))
            validate_probe(assertion)
            assertion = json.loads(clean(json.dumps(assertion, allow_nan=False), args.redact))
        except (ValueError, TypeError, UnicodeError) as exc:
            assertion_error = str(exc)
            assertion = None
    value = {"schema_version": 1, "event_type": "command", "id": event_id, "scope": args.scope,
             "label": args.label, "started_utc": started, "ended_utc": now(),
             "duration_seconds": round(time.monotonic() - start, 6), "timeout_seconds": args.timeout,
             "command": [clean(x, args.redact) for x in argv], "exit_code": code, "expected_exit_code": args.expect_exit,
             "timed_out": timed_out, "launch_error": launch_error, "command_expectation_met": command_ok,
             "outputs": output, "probe_result": assertion, "probe_parse_error": assertion_error,
             "probe_assertions_passed": assertion is not None and all(c["status"] == "passed" for c in assertion["cases"])}
    write_new(base / "events" / (event_id + ".json"), value)
    print(json.dumps({"receipt": "events/" + event_id + ".json", "command_expectation_met": command_ok,
                      "probe_assertions_passed": value["probe_assertions_passed"], "timed_out": timed_out}))
    return 0 if command_ok and (not args.probe_json or value["probe_assertions_passed"]) else 1


def validate_probe(value):
    if not isinstance(value, dict) or value.get("schema_version") != 1:
        raise ValueError("Probe JSON requires schema_version 1")
    context = value.get("context")
    if not isinstance(context, dict) or any(not isinstance(context.get(k), str) or not context[k] for k in ("fingerprint", "selinux", "service", "service_pid", "build_target", "source_manifest_sha256")):
        raise ValueError("Probe context requires fingerprint, selinux, service, service_pid, build_target, source_manifest_sha256 strings")
    if not re.fullmatch(r"[0-9a-f]{64}", context["source_manifest_sha256"]):
        raise ValueError("Probe context requires an actual lowercase SHA-256 source manifest digest")
    cases = value.get("cases")
    if not isinstance(cases, list) or not cases:
        raise ValueError("Probe cases must be nonempty")
    seen = set()
    for case in cases:
        if not isinstance(case, dict) or case.get("probe") not in PROBES or case["probe"] in seen:
            raise ValueError("Unknown or duplicate probe")
        seen.add(case["probe"])
        if case.get("status") not in ("passed", "failed", "skipped", "not-run") or not all(k in case for k in ("expected", "actual")):
            raise ValueError("Case requires status, expected and actual")
    if context["selinux"] != "Enforcing" and any(c["status"] == "passed" for c in cases):
        raise ValueError("Runtime passing assertions require SELinux Enforcing")


def artifact(args):
    base = bundle(args)
    source = Path(args.file)
    if source.is_symlink() or not stat.S_ISREG(source.stat().st_mode):
        raise ValueError("Artifact must be a regular non-symlink file")
    event_id = identifier(args.label)
    before = source.stat()
    if before.st_size > args.max_bytes:
        raise ValueError("Artifact exceeds configured size bound")
    source_hash = digest(source)
    destination = base / "artifacts" / event_id
    if not args.hash_only:
        with source.open("rb") as incoming, destination.open("xb") as outgoing:
            remaining = args.max_bytes + 1
            while remaining:
                data = incoming.read(min(1024 * 1024, remaining))
                if not data:
                    break
                outgoing.write(data)
                remaining -= len(data)
        if destination.stat().st_size > args.max_bytes or digest(destination) != source_hash:
            destination.unlink()
            raise ValueError("Artifact changed during copy or exceeded limit")
    after = source.stat()
    if (before.st_size, before.st_mtime_ns, before.st_ino) != (after.st_size, after.st_mtime_ns, after.st_ino):
        raise ValueError("Artifact changed during capture")
    value = {"schema_version": 1, "event_type": "artifact", "id": event_id, "label": args.label,
             "scope": args.scope, "captured_utc": now(), "sha256": source_hash, "bytes": before.st_size,
             "path": None if args.hash_only else str(destination.relative_to(base)), "hash_only": args.hash_only}
    write_new(base / "events" / (event_id + ".json"), value)
    print(json.dumps(value))
    return 0


def verify(args):
    base = bundle(args)
    errors = []
    files = {"attempt.json": digest(base / "attempt.json")}
    started, completed = set(), set()
    for p in sorted((base / "events").glob("*.json")):
        files[str(p.relative_to(base))] = digest(p)
        value = json.loads(p.read_text())
        if value.get("event_type") == "command-start":
            started.add(value["id"])
        elif value.get("event_type") == "command":
            completed.add(value["id"])
        refs = list(value.get("outputs", {}).values())
        refs += [output["tail"] for output in value.get("outputs", {}).values() if "tail" in output]
        if value.get("event_type") == "artifact" and not value.get("hash_only"):
            refs.append(value)
        for ref in refs:
            target = (base / ref["path"]).resolve()
            if not target.is_relative_to(base) or not target.is_file() or digest(target) != ref["sha256"]:
                errors.append(ref["path"])
            else:
                files[ref["path"]] = ref["sha256"]
    for path in sorted((base / "artifacts").iterdir()):
        if path.is_symlink() or not path.is_file():
            errors.append(str(path.relative_to(base)))
        else:
            files[str(path.relative_to(base))] = digest(path)
    print(json.dumps({"schema_version": 1, "verified_utc": now(), "integrity_passed": not errors,
                      "errors": errors, "files_sha256": files, "unfinished_commands": sorted(started - completed),
                      "limitation": "Integrity checks do not establish authenticity, completeness or probe acceptance."}, indent=2))
    return 1 if errors else 0


def positive(value):
    number = int(value)
    if number <= 0:
        raise argparse.ArgumentTypeError("Must be positive")
    return number


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="action", required=True)
    p = commands.add_parser("init")
    p.add_argument("--bundle", required=True)
    p.add_argument("--attempt-id", required=True)
    p.add_argument("--source-commit", required=True)
    p.add_argument("--build-target", required=True)
    p.set_defaults(handler=init)
    for action, handler in (("run", collect), ("artifact", artifact), ("verify", verify)):
        p = commands.add_parser(action)
        p.add_argument("--bundle", required=True)
        p.set_defaults(handler=handler)
        if action == "verify":
            continue
        p.add_argument("--label", required=True)
        p.add_argument("--scope", choices=SCOPES, required=True)
        if action == "artifact":
            p.add_argument("--file", required=True)
            p.add_argument("--hash-only", action="store_true")
            p.add_argument("--max-bytes", type=positive, default=64 * 1024 * 1024)
        else:
            p.add_argument("--timeout", type=positive, required=True)
            p.add_argument("--max-log-bytes", type=positive, default=1024 * 1024)
            p.add_argument("--max-tail-bytes", type=positive, default=64 * 1024)
            p.add_argument("--expect-exit", type=int, default=0)
            p.add_argument("--redact", action="append", default=[])
            p.add_argument("--probe-json", action="store_true")
            p.add_argument("command", nargs=argparse.REMAINDER)
    args = parser.parse_args()
    try:
        return args.handler(args)
    except (ValueError, OSError, KeyError) as exc:
        print(json.dumps({"collector_error": str(exc)}), file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
