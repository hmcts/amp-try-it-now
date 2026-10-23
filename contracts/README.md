# Contracts

Two kinds of file, and they are maintained in opposite ways.

| File | What it is | Maintained by |
|---|---|---|
| `try-it-now-sandbox.openapi.yml` | This engine's own discovery endpoint | Hand-written here, and it must track `ScenariosController` |
| `rcc-courthouses.openapi.yml` | The real API the `rcc` sandbox demonstrates | **Vendored.** A copy of a published `api-cp-*` tag — do not edit |

APIM merges one of each into a single published API, so a consumer sees the sandbox plumbing and
the real shape together rather than two half-APIs. See `../apim/README.md`.

## The sandbox contract describes this engine, not try-slc

try-slc has a file of the same shape, and it is **not** the same document. Its `/scenarios`
response carries `howToCall`, `caseUrn`, `audience`, `issuer` and `tokenEndpoint`; this engine's
carries `api`, `title`, and scenarios keyed by `request` and `operation`. Copying try-slc's across
would publish a response shape this service does not produce.

If `ScenariosController` changes shape, this file changes with it. Nothing enforces that
automatically — the engine has no generated interface to diff against, because it deliberately has
no per-API code at all.

## Every schema here is prefixed `Sandbox`

A merge resolves a name collision by silently letting one side win, publishing a schema under a
name whose definition came from the other spec. The prefix makes that impossible by construction,
rather than something to re-verify each time an API is added. `ErrorResponse` is the obvious one:
`api-cp-refdata-courthearing-courthouses` defines it, and so does nearly every other `api-cp-*`
contract.

## Adding an API

1. Vendor its `api-cp-*` spec at a pinned tag, with the provenance header the rcc file has.
2. Add the entry to `src/main/resources/registry/apis.yaml` and its fixtures.
3. Add a product and an API to `../apim/*.tfvars`, keyed by the same short code.
4. Register its resource app in `external-entra-id` before that environment runs `ENTRA`.

## Why there is no verify task

try-slc guards its vendored copy with `verifySlcContract`, which diffs against the `api-cp` jar on
its classpath. This engine has no such jar and should not get one: nothing in `src/main/java` names
an individual API, which is exactly what lets one deployment serve many. A per-API compile
dependency would undo that at the first of the hundred APIs this is meant to carry.

The cost is that a vendored copy can drift from its upstream tag unnoticed. The provenance header
in each file records the exact tag and the command to re-fetch it.
