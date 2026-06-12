---
title: Design principles
description: Eight constraints that keep Horizon-QA tests honest, portable, and diagnosable.
tags:
  - contributing
  - principles
---

# Design principles

These eight constraints exist to prevent the failure modes that quietly ruin mod testing infrastructure: tests that pass for the wrong reasons, tests that are brittle to refactors, and failures that tell you nothing useful.

Reference this document in PR reviews. If a contribution conflicts with one of these, cite the number and explain why the trade-off is justified.

## 1. Functional tests, not unit tests

A test boots a real server, places real blocks, and runs the real machine. We do not reimplement GT logic in mocks. If GT changes behaviour, our tests should catch it, not break silently because the mock has diverged.

## 2. Mock supply, not validation

Tests may use any means necessary to satisfy a machine's input preconditions. What they must never do is bypass or reimplement recipe-gating, efficiency, or output-routing logic.

> Supply gets the machine into a testable state. Validation is what you are testing.

## 3. Role-based addressing

Tests reference *"the input bus"* and *"the energy hatch"*, never *"the block at `(1, 1, 2)`"*, as the primary API. Positions live in the structure template. Tests that hardcode coordinates break on any structure rotation or layout change.

## 4. Wait on state, not ticks

Assert against observable machine state: formed, processing, idle, exploded, stalled. Hardcoded tick counts are acceptable only as **timeout budgets** (the maximum you are willing to wait before failing), never as a proxy for "the recipe finished by now."

## 5. Negative tests are load-bearing

The primary idiom is asserting **every tick** that something bad has *not* happened. Most real-world regressions are "this thing that should not have occurred, occurred." Design tests accordingly. See [Negative assertions](../guide/negative-tests.md).

## 6. Leave no trace

A test must not permanently mutate global registries, recipe maps, or player data. If a test requires a synthetic recipe or material, it must remove it during teardown. Global state mutation is the failure mode where one test silently poisons another, and the resulting flakiness is among the hardest to diagnose.

## 7. Tests organised by system, not by file

E2E tests live in a package hierarchy that mirrors the **system under test**, not the source class that implements it. Single-mod multiblock tests go under `multiblock/<machine-name>/`. Cross-mod compatibility tests go under `compatibility/<mod-a>_<mod-b>/`. The `examples/` directory in this repository is reserved for the framework's own documented examples.

See [Package layout](../reference/package-layout.md).

## 8. Failure output is part of the product

A passing test is cheap. **Every failure must be diagnosable from the JUnit XML alone**: what went in, what state the machine was in, what was expected versus observed, and the event sequence leading up to the failure. If a contributor cannot identify the root cause from the XML report, the failure output is a bug. Fix it before merging the feature.

See [Test event log](../reference/events.md) and [CI & JUnit reports](../guide/ci.md).
