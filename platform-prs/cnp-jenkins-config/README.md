# cnp-jenkins-config

## What this component needs: one file, not two

**`deployment-controls.yml`** — an entry. Prepared in `deployment-controls.snippet.yml`, with
its verified insertion point.

This is stronger than "nothing deploys without it": **the Jenkins job is never created without
it.** `jobdsl/organisations-beta.groovy` reads the file at CasC startup
(`loadApprovedRepos`), collects every `repositories[].repo` name — regardless of
`deployment-enabled` — and builds a `RegexSCMSourceFilterTrait` from them. The GitHub org scan
only picks up repos matching that allowlist. Its own comment: *"Only repositories present in this
allowlist will be picked up by Jenkins."* `deployment-enabled: true` is a separate flag consumed
later, to permit the deploy once the job exists.

**The GitHub topic is wrong, and it is in this area.** `amp-try-it-now` carries
`jenkins-cft-a-i`. **That topic does not exist.** `jobdsl/organisations-beta.groovy` declares
exactly three orgs:

| Topic | Jenkins folder |
|---|---|
| `jenkins-cft-a-c` | HMCTS - A to C |
| `jenkins-cft-d-i` | HMCTS - D to I |
| `jenkins-cft-j-z` | HMCTS - J to Z |

So the repo is in no org folder at all and the job would never be created even with the
deployment-controls entry. It should be **`jenkins-cft-a-c`**. try-slc and
`service-api-marketplace` both carry `jenkins-cft-j-z`, consistent with alphabetical on repo name
("t", "s"). Set it with:

```sh
gh api -X PUT /repos/hmcts/amp-try-it-now/topics -f 'names[]=jenkins-cft-a-c'
```

**`team-config.yml`** — nothing to do. `amp` is already registered (team "API Marketplace",
namespace `amp`, build notices to `#api-marketplace-build-notices`). It is product-scoped; there
is no per-component entry.

**`terraform-infra-approvals/amp-try-it-now.json`** — **not needed as `infrastructure/` stands
today**, and the onboarding plan was wrong to say otherwise.

The approvals mechanism matches a repo's terraform against `global.json` *plus* an optional
per-repo file, so a per-repo file only has to name what the global whitelist lacks. Checked on
23 October 2026 against `global.json` (22 resources, 55 modules):

| Declared in `infrastructure/` | In `global.json`? |
|---|---|
| `azurerm_resource_group` | yes |
| `azurerm_key_vault_secret` | yes |
| `cnp-module-key-vault?ref=master` | yes |
| `terraform-module-postgresql-flexible?ref=master` | yes |

So the file would be empty of anything meaningful. For comparison, try-slc's *merged* file
contains exactly one entry, `tls_private_key` — the only thing it declares that global lacks. The
snippet still sitting in try-slc's own `platform-prs/` lists the key vault module, the postgres
module and `azurerm_key_vault_secret`; that was an over-broad guess which was corrected before it
merged. Do not copy it.

## When the approvals file does become necessary

The moment `infrastructure/` generates a demo signing key. `DemoKeys` falls back to an ephemeral
key and logs that this "must never be the case in a deployed environment" — and nothing supplies
`demo.signing-key-jwk` today, in any environment. Fixing that means a `tls_private_key` resource,
which is not in `global.json`, which is exactly the one-line approvals file try-slc has.

**Do not copy try-slc's terraform for it verbatim.** try-slc stores
`tls_private_key.private_key_pem_pkcs8` and its `DemoSigningKey` sniffs the `-----BEGIN` prefix to
accept PEM *or* JWK. This engine's `DemoKeys` calls `RSAKey.parse` unconditionally and throws
`"demo.signing-key-jwk is set but could not be parsed as an RSA JWK"` on anything else — so a PEM
secret would stop the pod from starting. Either teach `DemoKeys` the PEM form or store a JWK.
