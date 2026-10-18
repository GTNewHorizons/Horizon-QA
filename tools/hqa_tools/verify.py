#!/usr/bin/env python3
"""Validate the schema-3 JSON/JUnit contract. Invalid or incomplete evidence is exit 2."""
import argparse
import json
import math
from pathlib import Path
import sys
import xml.etree.ElementTree as ET


def require(condition, message):
    if not condition:
        raise ValueError(message)


def verify(directory, selectors, earliest=0):
    directory = Path(directory).resolve()
    status = directory / "horizonqa-result.json"
    junit = directory / "TEST-horizonqa.xml"
    for path in (status, junit):
        require(path.is_file() and not path.is_symlink(), f"missing/unsafe report: {path}")
        require(path.stat().st_mtime >= earliest, f"stale report: {path}")
    result = json.loads(status.read_text(encoding="utf-8"))
    require(type(result) is dict and type(result.get("schemaVersion")) is int and result["schemaVersion"] == 3, "unsupported status schema")
    tests, issues, counts = result.get("tests"), result.get("issues"), result.get("counts")
    require(type(tests) is list and type(issues) is list and type(counts) is dict, "missing tests/issues/counts")
    require(all(type(test) is dict for test in tests), "malformed cases")
    require(all(type(issue) is dict and type(issue.get("fatalInCi")) is bool
        and all(type(issue.get(name)) is str and issue[name] for name in ("id", "kind", "source", "name", "message"))
        for issue in issues), "malformed issues")
    ids = [test.get("id") for test in tests]
    require(all(type(value) is str and value for value in ids) and len(ids) == len(set(ids)), "missing/duplicate case ids")
    allowed = {"passed", "failed", "timedOut", "skipped", "error", "notStarted", "running"}
    require(all(test.get("status") in allowed and type(test.get("required")) is bool for test in tests), "invalid case status/required")
    require(all(all(type(test.get(name)) is str and test[name] for name in ("classname", "name"))
        and type(test.get("ticks")) is int and test["ticks"] >= 0
        and type(test.get("timeSeconds")) in (int, float) and 0 <= test["timeSeconds"] <= 1e12
        and math.isfinite(test["timeSeconds"]) and type(test.get("output", [])) is list
        and all(type(line) is str for line in test.get("output", [])) for test in tests), "missing/invalid case metadata")
    require(all(":" in value and value.count(":") == 1 and all(value.split(":")) for value in ids), "invalid case id")
    failures = lambda test: test["status"] in {"failed", "timedOut"}
    tally = {"selectedTests": len(tests), "issues": len(issues)}
    for name, state in (("passed", "passed"), ("failed", "failed"), ("timedOut", "timedOut"), ("skipped", "skipped")):
        tally[name] = sum(test["status"] == state for test in tests)
    tally["incomplete"] = sum(test["status"] in {"notStarted", "running"} for test in tests)
    tally["requiredFailures"] = sum(failures(test) and test["required"] for test in tests)
    tally["optionalFailures"] = sum(failures(test) and not test["required"] for test in tests)
    tally["diagnosticErrors"] = sum(issue["fatalInCi"] for issue in issues)
    tally["junitFailures"] = tally["requiredFailures"]
    tally["junitErrors"] = len(issues) + sum(test["status"] in {"error", "running"} for test in tests)
    tally["junitSkipped"] = tally["optionalFailures"] + tally["skipped"] + sum(test["status"] == "notStarted" for test in tests)
    require(all(type(counts.get(name)) is int and counts[name] == number for name, number in tally.items()), "inconsistent status counts")
    expected = 2 if tally["incomplete"] or tally["diagnosticErrors"] or any(test["status"] == "error" for test in tests) else 1 if tally["requiredFailures"] else 0
    require(type(result.get("exitCode")) is int and result["exitCode"] == expected, "incorrect exit policy")
    require(result.get("status") == {0: "passed", 1: "failed", 2: "error"}[expected], "incorrect run status")
    require(tests or expected == 2, "empty execution cannot establish success")
    configuration = result.get("configuration", {})
    require(type(configuration) is dict, "malformed configuration")
    require(configuration.get("mode") == "ci", "expected reported CI mode")
    if selectors:
        require(configuration.get("tests") == selectors, "report used a different selector")
        tokens = [token.strip() for token in selectors.split(",")]
        require(all(token and "*" not in token for token in tokens), "invalid requested selector")
        if expected != 2:
            for token in tokens:
                require(any(matches(test, token) for test in tests), f"unexecuted selector: {token}")
        require(all(any(matches(test, token) for token in tokens) for test in tests), "unexpected selected cases")
    reports = result.get("reports", {})
    require(type(reports) is dict and all(type(reports.get(name)) is str for name in ("junit", "status")), "malformed report paths")
    require(Path(reports.get("junit", "")).resolve() == junit and Path(reports.get("status", "")).resolve() == status, "report paths do not match invocation")
    root = ET.parse(junit).getroot()
    cases = root.findall("testcase")
    require(root.tag == "testsuite" and len(cases) == len(tests) + len(issues), "incomplete JUnit suite")
    for attribute, number in (("tests", len(cases)), ("failures", tally["junitFailures"]), ("errors", tally["junitErrors"]), ("skipped", tally["junitSkipped"])):
        require(root.get(attribute) == str(number), f"inconsistent JUnit {attribute}")
    expected_cases = []
    for test in tests:
        state = test["status"]
        outcome = "failure" if failures(test) and test["required"] else "error" if state in {"error", "running"} else "skipped" if failures(test) or state in {"skipped", "notStarted"} else None
        expected_cases.append((test.get("classname"), test.get("name"), outcome))
    expected_cases.extend((issue.get("source"), issue.get("name"), "error") for issue in issues)
    observed = []
    for case in cases:
        outcomes = [tag for tag in ("failure", "error", "skipped") if case.find(tag) is not None]
        require(len(outcomes) <= 1, "JUnit case has contradictory outcomes")
        observed.append((case.get("classname"), case.get("name"), outcomes[0] if outcomes else None))
    require(observed == expected_cases, "JUnit cases/outcomes differ from JSON")
    return result


def matches(test, selector):
    test_id = test["id"]
    if ":" in selector:
        return test_id.startswith(selector) or (
            test["status"] == "skipped" and test.get("skipType") == "MISSING_REQUIRED_MOD"
            and "unresolvedCaseFamily=true" in test.get("output", []) and selector.startswith(test_id + "["))
    namespace, holder = test_id.split(":", 1)
    holders = [line[len("holderClass="):] for line in test.get("output", []) if line.startswith("holderClass=")]
    return selector == namespace or holder.startswith(selector + ".") or any(
        selector == name or selector == name.replace("$", ".") for name in holders)


def main(argv=None):
    parser = argparse.ArgumentParser(prog="hqa verify", description=__doc__)
    parser.add_argument("directory", type=Path)
    parser.add_argument("--tests", default="")
    parser.add_argument("--earliest", type=float, default=0)
    args = parser.parse_args(argv)
    try:
        result = verify(args.directory, args.tests, args.earliest)
        print(f"Horizon-QA {result['status']} exit={result['exitCode']} counts={result['counts']}")
        return result["exitCode"]
    except (ValueError, OSError, ET.ParseError, KeyError, TypeError) as error:
        print(f"Horizon-QA evidence rejected: {error}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
