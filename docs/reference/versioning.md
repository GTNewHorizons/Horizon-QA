---
title: Versioning
description: Versioning scheme, compatibility guarantees, and deprecation policy.
---

# Versioning

This project follows [Semantic Versioning 2.0.0](https://semver.org/). Given a version `MAJOR.MINOR.PATCH`:

- `MAJOR` increments on incompatible changes to the public API.
- `MINOR` increments on backward-compatible new functionality.
- `PATCH` increments on backward-compatible bug fixes.

Published versions and release notes are listed on [GitHub Releases](https://github.com/GTNewHorizons/Horizon-QA/releases). Check the notes before changing a pinned 0.x dependency.

## The 0.x line

The public API has no compatibility guarantees in 0.x releases. A minor bump (for example, 0.3 to 0.4) may include breaking changes to any type or method without a deprecation cycle.

!!! warning "Pin tightly in 0.x"
    Use an exact version or a strict range such as `[0.3.0,0.4.0)` in your `build.gradle`.
    API surfaces can shift between minor releases. Budget time to update call sites whenever you bump.

## The 1.x line and beyond

From 1.0.0 onward, the public API carries the following guarantees across minor versions within the same major line:

| Guarantee | What it means |
|---|---|
| Source compatibility | Existing source compiles without changes. Methods are not removed or renamed; parameter and return types do not change incompatibly. Adding overloads or default methods is permitted. |
| Binary compatibility | Compiled bytecode links against a newer minor release without recompilation. |

Major-version bumps (`1.x` to `2.0`) may break the public API and will be accompanied by a migration guide.

## Deprecation policy

The following process applies to public APIs from 1.0.0 onward:

1. The element is annotated `@Deprecated`. Its Javadoc says what replaces it.
2. It ships in at least one subsequent minor release to give dependents time to migrate.
3. It is removed in the next major release.

## Summary table

| Version range | Public API guarantee |
|---|---|
| 0.x | None; APIs may change in any minor bump. |
| 1.x+ (same major) | Source and binary compatible across minor versions. |
| Across major versions | No guarantee; see the migration guide. |
