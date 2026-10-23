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

* `../contracts/try-it-now-sandbox.openapi.yml` — `GET /scenarios`, the discovery endpoint.
* `../contracts/rcc-courthouses.openapi.yml` — a vendored copy of the demonstrated API's own
  contract at a pinned tag.

`expose_paths` allow-lists what is taken from the first; everything in the second is published.
Schemas are merged, and `merge()` resolves a collision by silently letting one side win — so
every schema in the sandbox contract is prefixed `Sandbox`, which makes a collision impossible
rather than something to re-verify each time an API is added. `ErrorResponse` is the one that
would otherwise bite: nearly every `api-cp-*` contract defines it.

## Entra validation is per environment, and must match the pod

`entra_tenant_id` set → every API policy gains `validate-jwt`. Unset → none does.

It has to agree with `auth.key-source` in that environment's flux values. Under `SELF_ISSUED` the
service signs tokens with its own key and issuer, and an Entra `validate-jwt` in front of it
rejects every one; under `ENTRA` without it the gateway forwards tokens it never checked.

`entra_audience` is **per API**, never shared. The role is `app.read` on every sandbox by design,
so the audience is the entire access boundary between them — a gateway-wide audience would make a
token minted for one sandbox valid against all of them, which is the hole the per-API resource
apps exist to close.

`sbox.tfvars` currently leaves both unset, because the sandbox pod runs `SELF_ISSUED`. The three
changes that flip it, in order, are listed in that file.

## Two applies per environment, the first time

APIM attaches the `administrators` group to a product the moment it is created, before Terraform
manages that association, so the first apply creates the product and then fails on the group it
did not create. The fix is an import — but an import block naming an object that does not exist
fails at **plan**, so it cannot be written ahead of time. Apply, add the entry to
`imports.tf` (the shape is in a comment there), apply again. The second is a one-line change.

## Prerequisites that are not in this repository

Neither exists for `hmcts/amp-try-it-now` yet, and without them every run fails at Azure login
before the terraform is evaluated:

1. **A federated credential** on the deploy app, scoped to `repo:hmcts/amp-try-it-now`.
2. **Three repository variables**, matching try-slc's:

   | Variable | Value |
   |---|---|
   | `AZURE_CLIENT_ID_SBOX` | `72a7651d-9d2a-4c04-9085-957efeab0e53` |
   | `AZURE_SUBSCRIPTION_ID_SBOX` | `bd2864ed-4f3e-45ed-9c6a-8d179674bab1` |
   | `AZURE_SUBSCRIPTION_SBOX` | `DTS-SPS-SBOX` |

   `TFSTATE_STORAGE_ACCOUNT_NONPROD` is an organisation variable and is inherited.
