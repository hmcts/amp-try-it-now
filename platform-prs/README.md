
## branch-ruleset.json

The GitHub branch ruleset for `master`, copied from try-slc's ruleset `24376232`: block deletion
and force-push, require a PR with conversation resolution (no approvals), and require the three
checks `Analyze (java)`, `CodeQL` and `pmd-analysis`.

**Applied 23 October 2026 as ruleset `25736208`**, verified byte-identical to try-slc's. It is a
repository **setting**, not a file GitHub reads — this copy exists so the configuration stays
reviewable and reproducible. To recreate it:

```sh
gh api -X POST /repos/hmcts/amp-try-it-now/rulesets --input platform-prs/branch-ruleset.json
```

It was held back until all three checks had gone green on PR #2. Requiring a check that has never
reported leaves `master` permanently unmergeable, and removing a ruleset needs admin.

One thing worth knowing if this is repeated on another repo: `codeql.yml` triggers only on
`pull_request` and a weekly cron, never on push to `master`, so `master` has no CodeQL baseline.
That turned out not to matter — the `CodeQL` check reported `pass` on the first PR regardless —
but it is worth confirming empirically rather than assuming, since getting it wrong blocks the
branch.

The Jenkins build check joins it after the first green CNP build — it does not exist until the
job is created, which needs the GitHub topic confirmed (Phase 1.3).
