---
title: Contributing
description: Setup, review expectations, and the bar for changes to Horizon-QA itself.
tags:
  - contributing
---

# Contributing

This section is for **framework contributors** and reviewers. Mod authors adopting the framework should start at [Getting started](../getting-started/index.md).

## Before you open a PR

1. Read [Design principles](principles.md). If a change conflicts with one of them, cite the principle number in the PR description and explain why.
2. Read the [Clean-room policy](legal.md): **no modern Minecraft decompiled source** under any circumstances.
3. Run the relevant tests via `examples`.
4. Update the documentation if you change author-facing behaviour (annotations, commands, events, JVM flags).

## Development setup

```bash
git clone https://github.com/GTNewHorizons/Horizon-QA.git
cd Horizon-QA
./gradlew :examples:runServer
```

Documentation preview:

```bash
pip install -r requirements.txt
mkdocs serve
```

The site is served at `http://127.0.0.1:8000` with live reload.

## Pull request checklist

- [ ] Behaviour matches an existing design principle or extends one deliberately
- [ ] Failure output remains actionable (event log + JUnit XML)
- [ ] Public API changes marked `@Stable` / `@Experimental` appropriately
- [ ] Examples mod updated if the feature is user-visible
- [ ] MkDocs pages updated for author-facing changes

## Edit documentation

Every page has an **Edit this page** action (Material `content.action.edit`) pointing at `docs/` on GitHub. Use it; the PR diff is the review surface.

## Related links

- [Examples mod](examples-mod.md)
- [Repository](https://github.com/GTNewHorizons/Horizon-QA)
- [Published docs](https://GTNewHorizons.github.io/Horizon-QA/)
