
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

## set-actions-variables.sh

Sets the per-environment Azure OIDC repository variables (`AZURE_CLIENT_ID_<ENV>`,
`AZURE_SUBSCRIPTION_ID_<ENV>`, `AZURE_SUBSCRIPTION_<ENV>`) for **sbox, aat and demo** — the whole
path for this service. Preview is deliberately not covered: it is CNP's ephemeral per-PR
environment, and publishing an APIM product per PR into a shared instance is not wanted here.

Ids, not secrets — OIDC removes the long-lived credential, not the identifier; trust comes from
Azure matching the token's subject against a federated credential. Values were taken from repos
already deploying to each environment and cross-checked against the scopes on the matching app
registration, rather than inferred: the **aat** registration is `DTS AMp Staging` and its
subscription is `DTS-SPS-STG`, neither of which follows from the environment name.

```sh
./platform-prs/set-actions-variables.sh
```

Only **sbox** does anything today. aat is inert until `apim/aat.tfvars` exists, because the
workflow builds its matrix from those filenames, and each environment also needs two federated
subjects in `azure-github-federation-config` before it can authenticate.

**Demo skips itself.** Its subscription id is known (`058bf954-…`, from the scopes on
`DTS AMp Demo`), but the appId and the subscription name are not recorded anywhere and the name
is not derivable — `aat` is `DTS-SPS-STG`, not `DTS-SPS-AAT`. Both are a PlatOps ask. The script
guards on the `ASK-PLATOPS-` placeholders and skips rather than setting a value that would fail
later with a confusing error; fill them in and the block runs.
