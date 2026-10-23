# CNP Onboarding Plan — amp/try-it-now

One Try It Now engine serving a sandbox for every registered API. Follows the path
`try-cp-crime-schedulingandlisting-courtschedule` took, and reuses its decisions where they apply.

## Names reference

| What | Name |
|---|---|
| GitHub repo | `amp-try-it-now` |
| GitHub topic | `jenkins-cft-a-i` — **unverified**, see Phase 1 |
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
| 1.3 | GitHub topic | ⚠️ set to `jenkins-cft-a-i` by the alphabetical rule. Every other AMP repo is `jenkins-cft-j-z` because their names start j–z; this one starts with `a`. **Confirm with PlatOps** — the topic decides the Jenkins folder, and a wrong one means the job is never created |
| 1.4 | Branch protection | ○ To do — add the Jenkins check after the first green build |

## Phase 2 — Terraform

| # | Step | Status | Notes |
|---|---|---|---|
| 2.1 | `infrastructure/` | ✅ | Component-scoped RG and vault, `amp-try-it-now-{env}` |
| 2.2 | `create_managed_identity = false` | ✅ | `cnp-module-key-vault` names it `{product}-{env}-mi` with no component segment, so it is product-wide. try-slc creates it as amp's first component; a second repo setting this true collides on the name |
| 2.3 | Own Postgres server | ✅ | `amp-tin-flexible-{env}`. Distinct from `amp-flexible` and try-slc's `amp-try-flexible` — two repos against one server is a state conflict |
| 2.4 | `terraform-infra-approvals/amp-try-it-now.json` | ○ To raise | Prepared in `platform-prs/`. Needs the postgres module, `cnp-module-key-vault` and `azurerm_key_vault_secret` |

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
