# APIM registration

Registers this engine's sandboxes with the HMCTS Shared Platform Services APIM. Applied by
`.github/workflows/terraform-apim.yaml`, not by the CNP Jenkins pipeline — that one owns
`infrastructure/`, which is a different configuration with a different backend.

State key is `amp-try-it-now/<env>/terraform.tfstate`, derived from the repository name by the
reusable workflow, so it cannot collide with try-slc's APIM state or with this repo's Jenkins
state.

## One product per API

try-slc registered a single product because it was a single-API service. This engine serves a
sandbox for **every** registered API from one deployment, and a product is what a consumer
subscribes to. A shared product would hand one subscription key access to every sandbox, and put
every API's rate limit in one bucket.

So `apim_products` and `apis` are both maps keyed by the API's short code — the same key
`src/main/resources/registry/apis.yaml` uses. A variable validation refuses a key in one that is
missing from the other.

| | |
|---|---|
| Product | `try-it-now-<api-cp product id>`, e.g. `try-it-now-cp-refdata-courthearing` |
| Gateway path | `amp/try/<short code>` |
| Backend | `https://amp-try-it-now.<env>.platform.hmcts.net/<short code>` |

**The backend URL ends in the short code, and that is not cosmetic.** The engine resolves which
API a request is for from the first path segment (`ApiAuthFilter.resolveApi`, and
`@RequestMapping("/{shortCode}/**")`). APIM appends the operation template to `service_url`, so
`/courthouses/{court_id}` arrives as `/rcc/courthouses/{court_id}`. Copy try-slc's bare host —
correct there, because try-slc has no prefix — and every operation 404s at a backend that is
working perfectly.

## What a consumer sees

One API, made of two specs merged at import time:

* `../contracts/try-it-now-sandbox.openapi.yml` — `GET /scenarios`, the discovery endpoint. The
  same file for every API.
* the demonstrated API's own contract, vendored at a pinned version: `rcc-courthouses.openapi.yml`
  for `rcc`, `slc-courtschedule.openapi.yml` for `slc`.

`expose_paths` allow-lists what is taken from the first; everything in the second is published.
Schemas are merged, and `merge()` resolves a collision by silently letting one side win — so
every schema in the sandbox contract is prefixed `Sandbox`, which makes a collision impossible
rather than something to re-verify each time an API is added. `ErrorResponse` is the one that
would otherwise bite: nearly every `api-cp-*` contract defines it.

## Entra validation is always on, and the pod must match

`entra_tenant_id` and each API's `entra_audience` are required, and every API policy carries
`validate-jwt`. The pod must run `auth.key-source` `ENTRA` in that environment's flux values: a
`SELF_ISSUED` pod's tokens are rejected by the gateway.

`entra_audience` is **per API**, never shared. The role is `app.read` on every sandbox by design,
so the audience is the entire access boundary between them — a gateway-wide audience would make a
token minted for one sandbox valid against all of them, which is the hole the per-API resource
apps exist to close.

## Two applies per environment, the first time

APIM attaches the `administrators` group to a product the moment it is created, before Terraform
manages that association, so the first apply creates the product and then fails on the group it
did not create. The fix is an import — but an import block naming an object that does not exist
fails at **plan**, so it cannot be written ahead of time. Apply, add the entry to
`imports.tf` (the shape is in a comment there), apply again. The second is a one-line change.

## slc is adopted, not created

`slc`'s product and API were registered by try-slc and carry **19 live subscriptions**. Keys
belong to a product, so recreating it would invalidate every one of them. `imports.tf` therefore
brings the existing product and its three groups into this state.

Two consequences worth knowing before editing the `slc` entries:

* **The API was renamed to `try-it-now-slc`.** try-slc registered it as `try-it-now---public-api`,
  derived from its spec title, and it was first adopted under that name. It now derives
  `try-it-now-slc` like every other API, so the Marketplace's developer portal link
  (`/api-details#api=try-it-now-<short code>`) is the same shape for all of them. APIM can only
  rename an API by recreating it, so that apply replaced the API, its product link and its policy,
  and `amp/try/slc` answered 404 for the minutes in between. The product, its groups and its
  subscriptions were not touched.
* **try-slc must release it first.** Its `apim/` configuration drops the product and API with
  `removed` blocks, which take them out of its state without destroying them. Adopting before that
  lands leaves two states believing they own the same resources.

## Prerequisites that are not in this repository

Both are in place, and were the two things that blocked the first apply:

1. **A federated credential** on the deploy app scoped to `repo:hmcts/amp-try-it-now`, added by
   [azure-github-federation-config#108](https://github.com/hmcts/azure-github-federation-config/pull/108).
2. **The `AZURE_*` repository variables**, set 23 October. `TFSTATE_STORAGE_ACCOUNT_NONPROD` is an
   organisation variable and is inherited.

Without either, a run fails at Azure login before any terraform is evaluated — so that is the
first thing to check if one ever does.
