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

  # ADOPTED FROM try-slc, NOT CREATED HERE.
  #
  # Every value below describes the product that already exists in sbox and carries 19 live
  # subscriptions. The name is try-slc's verbatim, and imports.tf brings the product, its three
  # groups, the API and the policy into this state. Change any of these and Terraform reconciles
  # a product consumers hold keys to - subscriptions_limit, approval_required and the group list
  # are therefore the live settings, not this repository's preferences.
  slc = {
    name = "try-it-now-cp-crime-schedulingandlisting"

    subscription_required         = true
    subscriptions_limit           = 20
    approval_required             = false
    published                     = true
    product_access_control_groups = ["developers", "administrators", "guests"]
  }
}

# ─── Entra validation ─────────────────────────────────────────────────────────────────────────
#
# The gateway validates Entra tokens, and may only do so while the pod does too. Both are keyed on
# AUTH_KEY_SOURCE=ENTRA in cnp-flux-config/apps/amp/amp-try-it-now/sbox.yaml, which has to be
# serving before this applies - with the pod still self-issuing, validate-jwt rejects every token
# it mints and sandbox stops answering.
#
# That ordering is one-way. Pod first then gateway has no broken window, because an Entra token
# passes an unconfigured gateway and the pod validates it. Gateway first does, so if this ever
# needs reverting, revert it before the flux side.
#
entra_tenant_id = "d44f885c-4fac-47bf-afde-d7d861ec4d7b"

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

    # Per-API, never shared: the audience is the access boundary between sandboxes, because the
    # role is app.read on all of them.
    entra_audience = "97c20454-55c1-45b0-88cd-268db572f615"

    # app.read is the only role amp-api-try-rcc defines, so it is the only one a token for that
    # audience can carry.
    required_roles = ["app.read"]
  }

  # ADOPTED FROM try-slc. See imports.tf.
  #
  # The one line that changes behaviour is service_url: the gateway path, the subscription keys
  # and the audience all stay exactly as they are, and traffic moves from try-slc's deployment to
  # this one. Reverting that single line puts it back.
  slc = {
    openapi_spec_path = "../contracts/try-it-now-sandbox.openapi.yml"
    expose_paths      = ["/scenarios"]
    merge_spec_path   = "../contracts/slc-courtschedule.openapi.yml"

    # try-slc derived this name from its spec title, which is how APIM came to hold an API called
    # try-it-now---public-api. It is kept verbatim because the name is the resource id: derive it
    # the way rcc does and Terraform would create a SECOND API on the same amp/try/slc path rather
    # than adopt this one. It is internal - a consumer sees the path and the display name - and
    # renaming it later is a deliberate recreate, which the product and its keys would survive.
    name         = "try-it-now---public-api"
    display_name = "Try It Now — Crime Scheduling and Listing Court Schedule"
    path         = "amp/try/slc"

    # THE CUTOVER. try-slc answers on the bare host because it serves one API; this engine resolves
    # the API from the first path segment, so the /slc suffix is what makes the same operations
    # arrive somewhere that recognises them.
    service_url = "https://amp-try-it-now.sandbox.platform.hmcts.net/slc"

    subscription_required = true

    # amp-api-try-slc, unchanged. The pod already validates against it (AUTH_AUDIENCE_SLC in
    # cnp-flux-config), so a token that works through try-slc's gateway today works through this
    # one, and no consumer has to mint anything differently.
    entra_audience = "c4c8974c-2800-4fe6-964f-dcd4c0820507"
    required_roles = ["app.read"]
  }
}
