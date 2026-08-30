# Upstream Maintenance

## Remotes and baseline

- Fork: `nonlog/showly`
- Upstream: `trakt/showly`
- Upstream default branch: `master`
- S0 baseline commit: `ec897b65b1b55c18ce24a755f83f894f422e559a`
- Fork development branch: `feat/ryot-foundation`

## Policy

The fork should remain easy to compare and merge with upstream.

- Keep upstream-shaped code and naming unless changing it is necessary for Ryot support.
- Avoid drive-by formatting, dependency churn and unrelated refactors.
- Prefer new adapters/components over rewriting stable upstream code.
- Make provider-neutral renames incrementally, only when a concrete Ryot slice needs them.
- Keep `master` releasable and close to upstream; develop Ryot work on feature branches and merge only after CI passes.
- Record the upstream commit used for each release or major integration stage.

## Sync procedure

Typical maintenance flow:

```text
fetch upstream
review upstream changes
merge/rebase into a dedicated update branch
run fork CI
resolve only real integration conflicts
merge into fork master
```

Do not force-push rewritten upstream history as a routine sync mechanism.

## CI note

The upstream `.github/workflows/android.yml` decrypts files using repository secrets. Forks do not inherit upstream secrets. S0.5 must provide a fork-safe verification path that can build/test without upstream signing secrets; release signing should remain a separate protected workflow.

## Commit identity

Commits created by the development agent use:

```text
Codex <codex@openai.com>
```

Set the identity at repository or per-command scope; never overwrite existing historical authorship.
