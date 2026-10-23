# amp-try-it-now

One Try It Now engine, serving a sandbox for every API in the AMP marketplace.

A sandbox answers from recorded examples. It holds no Common Platform data and has no route to a
real backend, which is what makes it safe to hand to anyone evaluating an API — and what makes it
safe to serve every API from one deployment.

**Registered today:** `slc`, `rcc`.

## The shape of it

Nothing under `src/main/java` names an individual API. An API is a row in
[`src/main/resources/registry/apis.yaml`](src/main/resources/registry/apis.yaml) and a set of
scenarios; adding one is configuration, not a code change.

A request resolves to three things — the API from its first path segment, the method, and a
canonical request key — and the recording found under them is returned exactly as it was approved:
status, body and headers.

```
GET /slc/case/TIN-ALLOCATED-01/courtschedule   ->  api_id=slc  GET  case/tin-allocated-01/courtschedule
GET /rcc/courthouses/C1/courtrooms/R2          ->  api_id=rcc  GET  courthouses/c1/courtrooms/r2
```

The key is the resolved request rather than a named parameter, because the estate has APIs taking
one path parameter, two, three and two query parameters — and two of them spell the same concept
`case_urn` and `caseURN`.

## The boundary

**Every API has its own audience, in both realms.** A token minted for `slc` presented on an `rcc`
path is refused on the audience — 401, before any role is read, and with no dependence on the other
API being configured correctly.

Roles are generic (`app.read`) *because* the audience is per API. If that ever inverts, two
sandboxes would have nothing between them;
[`AudienceBoundaryTest`](src/test/java/uk/gov/hmcts/amp/tin/auth/AudienceBoundaryTest.java) fails
loudly if it does.

| Result | Means |
| --- | --- |
| 200 | Recorded, entitled, served |
| 401 `invalid_token` | Wrong audience, bad signature, or expired |
| 403 `insufficient_scope` | Valid token, no role for this API |
| 404 | No such API, or nothing recorded for that request |

## Two realms

Validation is real in every environment and the same code runs in both. Only the key source differs.

| Realm | Who signs | Where |
| --- | --- | --- |
| `SELF_ISSUED` | This engine, with its own key | local, preview, AAT staging, AAT, demo |
| `ENTRA` | Microsoft Entra | sandbox |

Under `ENTRA` the demo token endpoint and its JWKS are **absent from the application**, not present
and refusing — the beans behind them are conditional on the self-issued realm.

**Demo is the last environment.** There is no production deployment and there must not be one.

## Running it locally

```bash
docker run -d --name tin-pg -e POSTGRES_PASSWORD=postgres -e POSTGRES_DB=tryitnow \
  -p 55432:5432 postgres:16-alpine

./gradlew bootRun
```

**55432, not 5432.** A natively installed Postgres on `127.0.0.1:5432` shadows a Docker container
published on the same port — the specific bind beats Docker's wildcard — and the app then fails with
`role "postgres" does not exist`, which reads like credentials and is really the wrong server.

There is no local Spring profile. What you run locally is configured exactly the way a deployed
environment is.

### Exercising it

```bash
# Discovery needs no token - it is how a consumer finds out what to call
curl localhost:8080/slc/scenarios
curl localhost:8080/rcc/scenarios

# A demo token, scoped to one API
TOKEN=$(curl -s -X POST 'localhost:8080/oauth2/v2.0/token?api=slc' | jq -r .access_token)
curl -H "Authorization: Bearer $TOKEN" localhost:8080/slc/case/TIN-ALLOCATED-01/courtschedule

# The same token at the wrong door - 401 on the audience
curl -H "Authorization: Bearer $TOKEN" localhost:8080/rcc/courthouses/TIN-ALLOCATED-01

# A token with no roles, to rehearse a 403 without a second registration
curl -s -X POST 'localhost:8080/oauth2/v2.0/token?api=slc&entitled=false'
```

## Changing a scenario

Edit its fixture and redeploy. Seeding compares a **content hash** of each fixture against the live
row, so an unchanged deployment writes nothing and an edited fixture supersedes the row it replaces.
The old row is archived rather than deleted, so what was served before is still there to look at.

The hash covers everything a consumer can observe — body, status, summary, operation and the request
key. A summary edit alone is still a change, because it shows on `/scenarios`.

**A fixture never overwrites a recording that did not come from one.** Provenance is `recorded_from`,
not the hash: a row ingested through the admin API is left exactly where it is, so a redeploy cannot
undo what a team published.

## Adding an API

1. An entry in `registry/apis.yaml` — short code, role, and the environment variable holding its
   Entra resource app id
2. Scenarios: a fixture in `src/main/resources/fixtures/` for the ephemeral environments
3. In a deployed environment, its audience variable (`AUTH_AUDIENCE_<CODE>`)

Startup fails if an API is registered under `ENTRA` without an audience. A blank audience would
accept a token minted for any resource, so the pod stops rather than quietly widening what it trusts.

## Publishing an API to APIM in a new environment

`apim/` registers one APIM product and API per sandbox, applied by
`.github/workflows/terraform-apim.yaml`. Promoting to an environment it has not run in before
takes **three** changes, and the workflow fails at Azure login — before terraform is evaluated at
all — if any one of them is missing:

1. **`apim/<env>.tfvars`.** The workflow builds its matrix from these filenames, so the file is
   what makes the environment exist as far as CI is concerned. It also decides the variable names
   in step 2: `demo.tfvars` -> `*_DEMO`.
2. **Three GitHub Actions repository variables**, named for that environment:
   `AZURE_CLIENT_ID_<ENV>`, `AZURE_SUBSCRIPTION_ID_<ENV>`, `AZURE_SUBSCRIPTION_<ENV>`. They are
   ids, not secrets. Each environment has its **own** app registration and therefore its own
   client id, so these cannot be copied between environments. `TFSTATE_STORAGE_ACCOUNT_NONPROD`
   is an organisation variable and is inherited; the tenant id is defaulted by the reusable
   workflow and is not set here.
3. **Two federated subjects** in `hmcts/azure-github-federation-config`'s
   `app-registrations.yaml`, under that environment's `DTS AMp <Env>` registration —
   `:pull_request` and `:ref:refs/heads/master`. Without these Azure refuses the OIDC token even
   though the variables are set.

The naming is not uniform and guessing it wastes an afternoon: the **aat** registration is called
`DTS AMp Staging`, and its subscription is `DTS-SPS-STG` rather than `DTS-SPS-AAT`. Take the
values from a repo that already deploys to that environment rather than inferring them —
`api-cp-crime-schedulingandlisting-courtschedule` has aat and preview. See `apim/README.md`.

**Try It Now stops at demo.** There is no production deployment and there must not be one, so
`apim/prod.tfvars` is not a file to create — the workflow fails deliberately if it finds one.

## Not built yet

- **The admin API** — ingest, review and publish. Until it exists, scenarios come from committed
  fixtures only, which means a redeploy rather than a call. It must not ship with a configurable issuer and no self-issued guard: a locally
  mintable admin token must be impossible to present in sandbox.
- **Ingest-time contract validation** against each API's published OpenAPI schema. This is what
  replaces parsing into a generated model, and is what lets the engine serve bodies verbatim while
  still guaranteeing they carry no field the contract does not define.
- **`X-Scenario` selection.** The key resolution is one branch away from it; both registered APIs
  are GET-only with path keys, so it buys nothing until `pcr` and `dl` arrive.
- **Per-environment scenario listing**, so "sandbox has six and demo has four" is answerable.
