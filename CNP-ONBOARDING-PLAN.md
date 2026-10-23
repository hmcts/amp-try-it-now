# CNP Onboarding Plan — amp/try-it-now

One Try It Now engine serving a sandbox for every registered API. Follows the path
`try-cp-crime-schedulingandlisting-courtschedule` took, and reuses its decisions where they apply.

## Names reference

| What | Name |
|---|---|
| GitHub repo | `amp-try-it-now` |
| GitHub topic | `jenkins-cft-a-c` — currently set to the non-existent `jenkins-cft-a-i`, see Phase 1.3 |
| Jenkins product / component | `amp` / `try-it-now` |
| Kubernetes namespace | `amp` |
| Helm release / chart | `amp-try-it-now` |
| Docker image | `hmctsprod.azurecr.io/amp/try-it-now:{tag}` |
| Flux config path | `apps/amp/amp-try-it-now/` |
| Key vault | `amp-try-it-now-{env}` (`amp-try-it-now-sbox` in sandbox) — component-scoped |
| Postgres server | `amp-tin-flexible-{env}`, database `tryitnow` |
| Managed identity | `amp-{env}-mi`, **created by try-slc**, consumed here |
| Sandbox host | `amp-try-it-now.sandbox.platform.hmcts.net` |
| Production | **none, deliberately** — demo is the last environment |

## Phase 1 — Repo

| # | Step | Status |
|---|---|---|
| 1.1 | Service, chart, Jenkinsfiles, tests | ✅ |
| 1.2 | `hmcts/amp-try-it-now` created and pushed | ✅ public, default `master` |
| 1.3 | GitHub topic | ⚠️ **WRONG — fix before anything else** | Currently `jenkins-cft-a-i`, which **does not exist**. `cnp-jenkins-config/jobdsl/organisations-beta.groovy` declares exactly three: `jenkins-cft-a-c`, `jenkins-cft-d-i`, `jenkins-cft-j-z`. Should be **`jenkins-cft-a-c`**. Until then the repo is in no Jenkins org folder and no job is created. `gh api -X PUT /repos/hmcts/amp-try-it-now/topics -f 'names[]=jenkins-cft-a-c'` |
| 1.4 | Static analysis | ✅ | try-slc's `.github/pmd-ruleset.xml`, CodeQL config and both workflows, copied unchanged. They produce the three check names the branch ruleset requires. The ruleset found 16 violations here and none in try-slc, so the code was brought up to it — no excludes, no `@SuppressWarnings` |
| 1.5 | Branch ruleset | ✅ Applied | Ruleset `25736208`, byte-identical to try-slc's `24376232`: block deletion and force-push, PR required with conversation resolution and no approvals, and the three checks `Analyze (java)`, `CodeQL`, `pmd-analysis`. Applied only after all three went green on PR #2. Payload kept in `platform-prs/branch-ruleset.json`. The Jenkins build check joins it after the first green CNP build |

## Phase 2 — Terraform

| # | Step | Status | Notes |
|---|---|---|---|
| 2.1 | `infrastructure/` | ✅ | Component-scoped RG and vault, `amp-try-it-now-{env}` |
| 2.2 | `create_managed_identity = false` | ✅ | `cnp-module-key-vault` names it `{product}-{env}-mi` with no component segment, so it is product-wide. try-slc creates it as amp's first component; a second repo setting this true collides on the name |
| 2.3 | Own Postgres server | ✅ | `amp-tin-flexible-{env}`. Distinct from `amp-flexible` and try-slc's `amp-try-flexible` — two repos against one server is a state conflict |
| 2.4 | `terraform-infra-approvals/amp-try-it-now.json` | ✅ Not needed | **This row was wrong.** Checked against `global.json` on 23 Oct 2026: `azurerm_resource_group`, `azurerm_key_vault_secret`, `cnp-module-key-vault` and `terraform-module-postgresql-flexible` are all globally whitelisted, so a per-repo file would add nothing. try-slc's *merged* file contains only `tls_private_key`; the snippet in its `platform-prs/` naming the modules was an over-broad guess corrected before merge. Becomes necessary only if 2.5 lands |
| 2.5 | Demo signing key | ⚠️ Missing | Nothing supplies `demo.signing-key-jwk` in any environment, so `DemoKeys` generates an ephemeral one and logs that this "must never be the case in a deployed environment" — tokens die on every redeploy. try-slc solves it with `tls_private_key` + a vault secret. **Its terraform cannot be copied verbatim:** it stores PKCS#8 PEM and try-slc's loader sniffs for it, while this engine's `DemoKeys` calls `RSAKey.parse` unconditionally and would refuse to start. Needed for aat and demo regardless, since those run SELF_ISSUED permanently |

## Phase 2b — cnp-jenkins-config

| # | Step | Status | Notes |
|---|---|---|---|
| 2.6 | `team-config.yml` | ✅ Already done | `amp` is registered (team "API Marketplace", namespace `amp`). Product-scoped — there is no per-component entry |
| 2.7 | `deployment-controls.yml` | ○ To raise | Prepared in `platform-prs/cnp-jenkins-config/`, with the verified insertion point: between `am-shared-infrastructure` and `backstage-hmcts-examples`. **Nothing deploys without it** |

## Phase 3 — cnp-flux-config

| # | Step | Status | Notes |
|---|---|---|---|
| 3.1 | HelmRelease, image repo and policy | ○ To raise | Prepared in `platform-prs/` |
| 3.2 | Image tag seed | ✅ decided | `prod-0000000-00000000000000`, not `:latest`. `tests/flux-v2-image-automation.sh` matches `prod-[a-f0-9]+-([0-9]+)`, which `:latest` fails, and a real prod tag cannot exist before the deploy this file enables |
| 3.3 | `workloadClientID` | ✅ decided | **Omitted.** try-slc's merged sbox patch has none — only its stale prepared copy pinned one, and that was apim's. The chart default plus `enableKeyVaults: true` is what actually shipped |
| 3.4 | Sandbox patch | ○ To raise | `SELF_ISSUED` — see 3.5 |
| 3.5 | Sandbox token realm | ⚠️ | Cannot be `ENTRA` yet. The registry resolves a per-API audience and **fails startup** if one is missing; `rcc` has no Try It Now resource app. Register `amp-api-try-rcc` first, then flip |
| 3.6 | AAT and demo patches | ○ Later | Sandbox first |
| 3.7 | Prod overlay | ⛔ Never | Demo is the last environment |

## Phase 4 — Verify

| # | Step | Command |
|---|---|---|
| 4.1 | Jenkins picks up the repo | — |
| 4.2 | First build green | — |
| 4.3 | Health | `GET https://amp-try-it-now.sandbox.platform.hmcts.net/health` |
| 4.4 | Discovery | `GET /slc/scenarios` and `GET /rcc/scenarios`, no token |
| 4.5 | Full journey | demo token → `GET /slc/case/TIN-ALLOCATED-01/courtschedule` |

Bruno coverage is in `amp-bruno-collection`, folder `cnp/try-it-now` — fill `tinBaseUrl` in
`SANDBOX.bru` once the host answers and the whole folder runs against it.

## Not built yet

The admin API, ingest-time contract validation, and `X-Scenario` selection. Until the admin API
exists, scenarios come from committed fixtures and a redeploy. See the README.
