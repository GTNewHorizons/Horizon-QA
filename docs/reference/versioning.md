---
title: Versioning
description: Versioning scheme, @Stable and @Experimental guarantees, and deprecation policy.
tags:
  - reference
  - stability
---

# Versioning

This project follows [Semantic Versioning 2.0.0](https://semver.org/). Given a version `MAJOR.MINOR.PATCH`:

- `MAJOR` increments on incompatible changes to any `@Stable` API.
- `MINOR` increments on backward-compatible new functionality.
- `PATCH` increments on backward-compatible bug fixes.

## The 0.x line

The entire public API is [`@Experimental`](annotations.md#stable-experimental) in every 0.x release. That means a minor bump (e.g. 0.3 to 0.4) may include breaking changes to any type or method without a deprecation cycle.

!!! warning "Pin tightly in 0.x"
    Use an exact version or a strict range such as `[0.3.0,0.4.0)` in your `build.gradle`.
    API surfaces can shift between minor releases. Budget time to update call sites whenever you bump.

The target for the first `@Stable` graduation is **1.0.0**.

## The 1.x line and beyond

Once 1.0.0 ships, any API element marked `@Stable` carries the following guarantees across minor versions within the same major line:

| Guarantee | What it means |
|---|---|
| Source compatibility | Existing source compiles without changes. Methods are not removed or renamed; parameter and return types do not change incompatibly. Adding overloads or default methods is permitted. |
| Binary compatibility | Compiled bytecode links against a newer minor release without recompilation. |

`@Experimental` elements within a 1.x release remain opt-in-breakable. They can change or be removed in any minor bump, with no deprecation required.

Major-version bumps (`1.x` to `2.0`) may break any API, including `@Stable` ones, and will be accompanied by a migration guide.

## Deprecation policy

The following process applies to `@Stable` APIs from 1.0.0 onward:

1. The element is annotated `@Deprecated`. Its Javadoc says what replaces it.
2. It ships in at least one subsequent minor release to give dependents time to migrate.
3. It is removed in the next major release.

`@Experimental` elements are exempt from this cycle and can be removed in any minor release.

## Summary table

| Version range | `@Stable` guarantee | `@Experimental` guarantee |
|---|---|---|
| 0.x | None. Everything is experimental. | May change in any minor bump. |
| 1.x+ (same major) | Source and binary compatible across minor versions. | May change in any minor bump. |
| Across major versions | No guarantee; see migration guide. | No guarantee. |
