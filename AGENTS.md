# Horizon-QA agent guide

## Non-negotiable constraints

- Treat [the design principles](docs/contributing/principles.md) and [clean-room policy](docs/contributing/legal.md) as hard constraints. Tests exercise the real server/GT behavior: mock supply, not validation; address fixtures by labels, wait on state rather than fixed ticks, and clean every global mutation. Never consult or copy decompiled modern Minecraft code.
- Minecraft 1.7.10 still runs Java 8 bytecode. Jabel permits modern syntax, not post-Java-8 JDK APIs; annotate every `record` with `@Desugar`. `Tags.java` is Gradle-generated—do not add or edit it.
- Keep common/dedicated-server load paths free of eager `net.minecraft.client` and LWJGL references; client registration belongs behind `ClientProxy` or a narrow `@SideOnly(CLIENT)` method. A package name is not proof of side (`visual.SelectionBoxRenderer` is server-side).
- Prefer `GTAdapter` for version-sensitive GregTech internals. A GT5u bump must audit both `GT5UnofficialAdapter` and the direct-touch `api/gt` facades, then run them in-game; compilation cannot catch reflective or linkage mismatches.
- START callbacks run before the world tick; END callbacks, polling, and timeout evaluation run after it. Preserve that ordering in mixin, runner, instance, and sequence changes.
- Treat a structure's JSON and optional NBT sidecar as one compatibility artifact. Preserve loader precedence (`.snbt`, `.nbt`, then legacy sidecars) and rotate dimensions, blocks, tile/entity NBT, and labels together.

## Public contracts

- `docs/` is canonical; `README.md` is only a summary and `site/` is generated. Any author-facing API, annotation, event, command, JVM property, or structure-format change must update the matching surfaces: Javadoc for Java APIs, its MkDocs reference, and a focused runnable `examples/` case when applicable. New 0.x author APIs remain `@Experimental`; add new pages to `mkdocs.yml`.
- Results are one external protocol from `CaseResult`/`IssueResult` through `RunResult` to console, JUnit XML, and status JSON. Optional failures remain exit `0`/JUnit-skipped, required failures or timeouts exit `1`, and infrastructure/incomplete/reporting errors exit `2`; update all reporters, tests, and docs together, and bump `schemaVersion` for breaking JSON shape or meaning changes.

## Proving a change

- Run `./gradlew build` for compilation, unit tests, Checkstyle, and Spotless across the root and `examples`; this does **not** boot Minecraft.
- Exercise Minecraft/Forge/GT, mixin/lifecycle, or structure behavior with a focused `./gradlew :examples:runServer --mcJvmArgs="-Dhorizonqa.mode=ci -Dhorizonqa.tests=<exact-test-id>"`. Pass every `horizonqa.*` flag through `--mcJvmArgs`, not bare Gradle `-D`. Interactive `/horizonqa run` bypasses batch ordering and hooks; use reported/CI mode when those matter. Some optional failing/timeout examples are intentional.
- For documentation run `mkdocs build --strict`; for public API changes also run `./gradlew javadoc`.
