api_mgmt_rg   = "rg-sps-platform-sbox"
api_mgmt_name = "sps-api-mgmt-sbox"

# One product per API, keyed by short code - the same key src/main/resources/registry/apis.yaml
# uses. The name follows try-slc's: try-it-now-<the api-cp product id>.
apim_products = {
  rcc = {
    name = "try-it-now-cp-refdata-courthearing"

    # The product requires a subscription, so the Marketplace issues keys against it, and the API
    # below requires one too. Both are true, so there is no open path: every operation needs a key,
    # including GET /scenarios. That is a product decision taken the same way try-slc took it - a
    # sandbox consumer arrives through the Marketplace and already holds a key by the time they
    # get here - and it is separate from the token: /scenarios stays exempt from validate-jwt, so
    # discovery needs a key but no Entra token.
    subscription_required         = true
    subscriptions_limit           = 20
    approval_required             = false
    published                     = true
    product_access_control_groups = ["developers", "administrators", "guests"]
  }
}

# ─── Entra validation: deliberately unset ─────────────────────────────────────────────────────
#
# The sandbox deployment runs AUTH_KEY_SOURCE=SELF_ISSUED
# (cnp-flux-config/apps/amp/amp-try-it-now/sbox.yaml), so the gateway must not validate Entra
# tokens: the service signs its own with its own key and issuer, and validate-jwt would reject
# every one of them. Setting entra_tenant_id here before the pod is flipped breaks the sandbox.
#
# The flip is three changes that have to land together, in this order:
#
#   1. external-entra-id#36 APPLIES. Merged is not applied - amp-api-try-rcc does not exist, and
#      has no app id, until the pipeline runs. Then:
#
#        az ad app list --display-name amp-api-try-rcc --query '[0].appId' -o tsv
#
#   2. cnp-flux-config: AUTH_KEY_SOURCE=ENTRA, AUTH_TENANT_ID, AUTH_ISSUER, and one
#      AUTH_AUDIENCE_<CODE> per registered API. key-source is service-wide, so this needs
#      AUTH_AUDIENCE_SLC *and* AUTH_AUDIENCE_RCC set at once - the registry fails startup if any
#      API it serves has no audience.
#
#   3. Uncomment the two lines below, with the id from step 1.
#
# entra_tenant_id = "d44f885c-4fac-47bf-afde-d7d861ec4d7b"

apis = {
  rcc = {
    openapi_spec_path = "../contracts/try-it-now-sandbox.openapi.yml"

    # Only /scenarios from the sandbox contract. It is the one path in that document, so this is
    # belt and braces today - it matters when the admin surface lands, which must never reach the
    # public product.
    expose_paths = ["/scenarios"]

    # The API being demonstrated: a vendored copy of api-cp-refdata-courthearing-courthouses at
    # v1.1.1. Its paths and schemas are merged into the imported document so a consumer sees one
    # API rather than two half-APIs.
    merge_spec_path = "../contracts/rcc-courthouses.openapi.yml"

    display_name = "Try It Now — Refdata Court Hearing Courthouses"
    path         = "amp/try/rcc"

    # NOTE THE /rcc SUFFIX. One deployment serves every registered API, and the engine resolves
    # which one from the first path segment (ApiAuthFilter.resolveApi, and
    # @RequestMapping("/{shortCode}/**")). APIM appends the operation template to this URL, so
    # /courthouses/{court_id} arrives as /rcc/courthouses/{court_id}, which is the path the engine
    # answers. The bare host - which is what try-slc uses, because try-slc has no prefix - would
    # 404 every operation and read as a backend fault.
    service_url = "https://amp-try-it-now.sandbox.platform.hmcts.net/rcc"

    subscription_required = true

    # Set in step 3 above, with amp-api-try-rcc's app id. Per-API, never shared: the audience is
    # the access boundary between sandboxes, because the role is app.read on all of them.
    # entra_audience = "..."

    # app.read is the only role amp-api-try-rcc defines, so it is the only one a token for that
    # audience can carry.
    required_roles = ["app.read"]
  }
}
