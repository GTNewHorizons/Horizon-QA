---
title: Horizon-QA
description: End-to-end GameTest framework for GT New Horizons on Minecraft 1.7.10.
hide:
  - navigation
  - toc
---

<div class="horizon-home" markdown>

<div class="horizon-intro" markdown>

<div class="horizon-intro__copy" markdown>

<p class="horizon-eyebrow">Horizon-QA</p>

# End-to-end testing for GT New Horizons

Horizon-QA brings a GameTest-style API to Minecraft 1.7.10. It runs real multiblocks, recipes, logistics, and cross-mod interactions on a development server, with local diagnostics and CI reports built in.

<div class="horizon-actions" markdown>

[Write your first test](getting-started/index.md){ .md-button .md-button--primary }
[Learn how it runs →](concepts/index.md){ .horizon-text-link }

</div>

<ul class="horizon-meta">
  <li>Forge 1.7.10</li>
  <li>Java 8 bytecode</li>
  <li>Interactive and CI execution</li>
</ul>

</div>

<div class="horizon-intro__example" markdown>

<p class="horizon-example-label">Negative formation test</p>

```java
@GameTest(
    template = "ebf_no_coils",
    timeoutTicks = 60
)
public static void rejectsMissingCoils(
    GameTestHelper helper
) {
    helper.gtnh()
        .multiblock(helper.pos("controller"))
        .assertNeverForms(
            "EBF formed without coils");
}
```

<p class="horizon-example-caption">
The exported fixture is real, GregTech performs the structure check, and the invariant is observed for the complete test window.
</p>

</div>

</div>

## Choose a path

<div class="grid horizon-tracks" markdown>

<div class="card horizon-track" markdown>

:material-rocket-launch-outline:{ .lg .middle } **Use Horizon-QA**

---

Follow a short, linear path from dependency setup to one passing test:

1. [Add Horizon-QA to your mod](getting-started/mod-setup.md)
2. [Write your first test](getting-started/first-test.md)
3. [Run it locally or in CI](getting-started/enable-and-run.md)

[Open the getting-started path →](getting-started/index.md)

</div>

<div class="card horizon-track" markdown>

:material-map-marker-path:{ .lg .middle } **Understand the framework**

---

Learn what runs on the server and where the testing boundaries are:

- [Runtime lifecycle](concepts/runtime-lifecycle.md)
- [Interactive and reported execution](concepts/execution-model.md)
- [Fixtures, coordinates, and isolation](concepts/fixtures-and-isolation.md)

[Explore the learning path →](concepts/index.md)

</div>

</div>

## What it helps you validate

<ul class="horizon-capabilities">
  <li><span>Machine formation</span> Catch incomplete, invalid, or rotation-sensitive multiblocks.</li>
  <li><span>Recipes and maintenance</span> Verify start conditions, processing, output, and maintenance gating.</li>
  <li><span>Logistics and compatibility</span> Exercise item, fluid, tile-entity, and cross-mod contracts.</li>
  <li><span>Transient behavior</span> Detect forbidden states that appear for only one server tick.</li>
</ul>

## Designed for useful failures

Tests can keep failed cells available for inspection, point overlays at relevant blocks, and attach an ordered event trace to reported results. CI runs produce JUnit XML and status JSON with stable exit codes.

Use the [guides](guide/index.md) when solving a testing task, or go directly to the [reference](reference/index.md) when you already know which API, command, or property you need.

!!! info "Clean-room implementation"

    Horizon-QA is an independent implementation inspired by modern GameTest ergonomics. It does not contain decompiled Mojang source. Framework contributors should read the [clean-room policy](contributing/legal.md).

</div>
