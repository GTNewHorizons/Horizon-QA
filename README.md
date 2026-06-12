# Horizon QA

Horizon QA is an end-to-end testing framework for GTNH. It implements the modern Minecraft GameTest API on 1.7.10, providing a specialized environment to validate multiblocks, machine logic, and logistics.

**📖 Documentation: <https://GTNewHorizons.github.io/Horizon-QA/>** with getting started, guides, the full command/property reference, and [Javadoc](https://GTNewHorizons.github.io/Horizon-QA/javadoc/index.html). This README is a summary; the website is the canonical documentation.

## Quick Example: Testing Negative Cases

A key strength of Horizon QA is verifying that machines *don't* behave incorrectly. The snippet below checks that an Electric Blast Furnace fails to form when its coils are missing, exactly the kind of regression a unit test can't catch:

```java
@GameTest(template = "ebf_no_coils", timeoutTicks = 60)
public static void doesNotFormWithoutCoils(GameTestHelper helper) {
    Multiblock ebf = helper.gtnh().multiblock(at(1, 0, 0));
    helper.onEachTick(() -> helper.assertFalse(ebf.isFormed(), "EBF formed without coils"));
    helper.succeedAtTimeout();
}
```

`onEachTick` re-runs the assertion every tick for the full 60-tick window, so any transient formation is caught immediately. `succeedAtTimeout` marks the test passed only if it reaches the end without triggering the assertion.

## Features
* **Java API**: Define tests using standard `@GameTest` annotations and the GameTestHelper class for assertions.
* **GTNH Integration**: Built-in support for handling EU, maintenance states, and time-warping for machine processing.
* **Structure Support**: Tools to export, place, and verify multiblock structures via JSON or NBT.
* **Visual Feedback**: In-game overlays, beacons, and ghost blocks to identify test failures visually.
* **CI/CD Integration**: Generates JUnit-compatible XML reports for automated build pipelines.
* **Horizon Wand**: An in-game tool to help with area selection and structure management.

## Usage

1. Build your test structure in-game ([guide](https://GTNewHorizons.github.io/Horizon-QA/guide/structures/)).
2. Select the area with the **Horizon Wand** and use `/qa export <name>` to create a template.
3. Write a Java test class using `@GameTest` to define the logic and assertions ([first test](https://GTNewHorizons.github.io/Horizon-QA/getting-started/first-test/)).
4. Run tests using the `/qa runall` command and view results in-game or in the build logs. For report files from a manually-started batch, pass `-Dhorizonqa.mode=report`; for headless runs, pass `-Dhorizonqa.mode=ci` to the server JVM ([CI guide](https://GTNewHorizons.github.io/Horizon-QA/guide/ci/)).

## Legal Disclaimer & Clean-Room Implementation

**Horizon-QA** is an independent, clean-room implementation of a testing framework for Minecraft 1.7.10. While the API structure, class names, and annotations are heavily inspired by modern Minecraft's GameTest framework to ensure developer familiarity and ease of use, the underlying codebase is entirely original.

* **No Proprietary Code:** This project was developed entirely from scratch by observing public documentation, videos, and the external behavior of modern testing tools. It contains **zero** decompiled or proprietary source code from Mojang/Microsoft.
* **Transformative Work:** The framework safely translates modern API concepts into the fundamentally different 1.7.10 architecture and includes custom, transformative extensions built specifically for GregTech New Horizons (GTNH).
* **Licensing:** Because the implementation logic is 100% original, this project is legally and safely distributed under the **MIT License**.

### Notice to Contributors
To protect the legal integrity of this project, please **do not** reference, decompile, or copy any source code from modern Minecraft versions when submitting Pull Requests. Any PR found to contain proprietary Mojang code will be immediately rejected.
