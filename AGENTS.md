# Agent Guidelines

## Purpose

This repository is a maintainable fork of `trakt/showly` adding optional self-hosted Floppy tracking.

Before implementing Floppy work, read:

- `docs/PRODUCT.md`
- `docs/ARCHITECTURE.md`
- `docs/FLOPPY-INTEGRATION.md`
- `docs/HANDOFF.md`
- `docs/UPSTREAM.md`
- `docs/ROADMAP.md`

## Rules

- Preserve upstream behavior unless the current milestone explicitly changes it.
- Keep diffs narrow. Do not perform unrelated cleanup, formatting or dependency upgrades.
- Do not remove Trakt in early milestones.
- Do not migrate local database keys away from Trakt IDs without an explicit roadmap stage, migration design and tests.
- Do not make Floppy internal database IDs part of Showly's canonical domain identity.
- Prefer TMDB/TVDB/IMDb plus season/episode coordinates at external integration boundaries.
- Do not commit server URLs containing secrets, tokens, API keys, signing keys or local credential files.
- GitHub Actions is the canonical build/test environment. Local builds are optional diagnostics, not a release prerequisite.
- Every feature branch must remain reviewable against upstream.
- Update `docs/HANDOFF.md` after each verified milestone, meaningful design change, blocker, or branch transition so a new session can resume without reconstructing history.

## Git identity

For new commits created by Codex, ensure both author and committer resolve to:

```text
Codex <codex@openai.com>
```

Do not rewrite authorship of existing commits.

## Verification

For code changes, run the fork-safe GitHub CI required by the active milestone. A green local build alone is not sufficient for merge/release decisions.
