"""Validate the actionable ticket graph, fields and honest completion boundary."""
import json
from pathlib import Path

data = json.loads((Path(__file__).parent / "tickets-v2.json").read_text())
tickets = data["tickets"]
by_key = {t["key"]: t for t in tickets}
assert len(by_key) == len(tickets), "duplicate ticket key"
assert {t["parent"] for t in tickets} == {f"B{i:02}" for i in range(1, 18)}
required = {"key", "title", "parent", "stage", "priority", "role", "estimateMin",
            "estimateMax", "requirements", "dependsOn", "value", "scope",
            "acceptance", "verification", "evidence", "risks", "status"}
visited, visiting = set(), set()


def visit(key):
    assert key in by_key, f"missing prerequisite {key}"
    assert key not in visiting, f"cycle at {key}"
    if key in visited:
        return
    visiting.add(key)
    for dependency in by_key[key]["dependsOn"]:
        visit(dependency)
    visiting.remove(key)
    visited.add(key)


for t in tickets:
    assert required <= t.keys(), t["key"]
    assert 0 < t["estimateMin"] <= t["estimateMax"] <= 16, t["key"]
    assert len(t["acceptance"]) >= 5, t["key"]
    assert t["verification"] and t["evidence"] and t["risks"]
    assert t["requirements"] and set(t["requirements"]) <= {f"R{i:02}" for i in range(1, 23)}
    assert t["stage"] in {"reference", "G1", "G2", "G3"}
    assert t["priority"] in {"P0", "P1", "P2"}
    assert t["status"] == ("verified" if t["key"] == "REF01" else "planned")
    assert all(v["proposed"] == (t["key"] != "REF01") for v in t["verification"])
    visit(t["key"])
print(f"PASS: {len(tickets)} detailed tickets; 17 epics; acyclic prerequisites; only REF01 verified")
for stage in ["reference", "G1", "G2", "G3"]:
    selected = [t for t in tickets if t["stage"] == stage]
    print(stage, len(selected), f'{sum(t["estimateMin"] for t in selected)}–{sum(t["estimateMax"] for t in selected)}h')
