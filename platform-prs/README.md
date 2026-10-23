
## branch-ruleset.json

The GitHub branch ruleset for `master`, copied from try-slc's ruleset `24376232`: block deletion
and force-push, require a PR with conversation resolution (no approvals), and require the three
checks `Analyze (java)`, `CodeQL` and `pmd-analysis`.

It is a repository **setting**, not a file GitHub reads — this copy exists so the configuration is
reviewable and reproducible. Apply with:

```sh
gh api -X POST /repos/hmcts/amp-try-it-now/rulesets --input platform-prs/branch-ruleset.json
```

**Only after those three checks have gone green on a PR.** Requiring a check that has never
reported leaves `master` permanently unmergeable, and removing a ruleset needs admin.

The Jenkins build check joins it after the first green CNP build — it does not exist until the
job is created, which needs the GitHub topic confirmed (Phase 1.3).
