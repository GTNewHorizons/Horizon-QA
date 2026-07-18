---
title: Clean-room policy
description: Legal constraints on contributions to Horizon-QA. No Mojang decompiled source, ever.
---

# Clean-room policy

## Disclaimer

**Horizon-QA** is an independent, clean-room implementation of a testing framework for Minecraft 1.7.10. API structure, class names, and annotations are inspired by modern Minecraft's GameTest framework for developer familiarity, but the implementation itself is original.

## What that means

- **No proprietary code.** The framework is developed from public documentation, talks, videos, and observed behavior, never from decompiled Mojang sources.
- **Transformative work.** Concepts are adapted to the 1.7.10 architecture with GTNH-specific extensions (EU supply, maintenance, time-warp, typed event log).
- **License.** MIT. See the repository `LICENSE` file.

## Notice to contributors

!!! danger "No decompiled modern Minecraft code"

    Do not reference, decompile, or copy source code from modern Minecraft versions into pull requests. PRs that include proprietary Mojang code will be **rejected** regardless of how minor the contribution looks.

When implementing new API surface:

- Match the **behavioral** ergonomics of modern GameTest where helpful to authors.
- Write **new** 1.7.10 / Forge / GTNH-specific code paths from scratch.
- Document author-facing APIs on this site and in the Javadoc.

## Questions

If you are unsure whether a contribution is safe under this policy, open an issue **before** writing a large patch. Wasted clean-room rewrites are expensive; a five-minute conversation up front is not.
