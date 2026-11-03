api_mgmt_rg   = "rg-sps-platform-demo"
api_mgmt_name = "sps-api-mgmt-demo"

apim_products = {
  slc = {
    name = "try-it-now-cp-crime-schedulingandlisting"

    subscription_required         = true
    subscriptions_limit           = 20
    approval_required             = false
    published                     = true
    product_access_control_groups = ["developers", "administrators", "guests"]
  }

  rcc = {
    name = "try-it-now-cp-refdata-courthearing"

    subscription_required         = true
    subscriptions_limit           = 20
    approval_required             = false
    published                     = true
    product_access_control_groups = ["developers", "administrators", "guests"]
  }
}

# ─── Entra validation ─────────────────────────────────────────────────────────────────────────
#
# entra_tenant_id is deliberately absent, so no policy here gains validate-jwt. The demo pod is
# still SELF_ISSUED until cnp-flux-config#48796 reconciles, and a gateway validating Entra tokens
# now would reject every token the pod is currently minting.
#
# Unvalidated at the gateway is the safe direction, and it is the same ordering sbox followed: an
# Entra token passes through a gateway that is not checking and the pod validates it. So this can
# land before the flux side, and adding entra_tenant_id afterwards is one line with no window in
# which demo is broken.

apis = {
  slc = {
    openapi_spec_path = "../contracts/try-it-now-sandbox.openapi.yml"
    expose_paths      = ["/scenarios"]
    merge_spec_path   = "../contracts/slc-courtschedule.openapi.yml"

    # No name pin, unlike sbox. Nothing is adopted here - try-slc never registered in demo - so the
    # API is created fresh and derives its name the way rcc does in sandbox.
    display_name = "Try It Now — Crime Scheduling and Listing Court Schedule"
    path         = "amp/try/slc"

    # The /slc suffix is load-bearing: one deployment serves every registered API and the engine
    # resolves which one from the first path segment. The bare host 404s every operation.
    service_url = "https://amp-try-it-now.demo.platform.hmcts.net/slc"

    subscription_required = true

    # amp-api-try-slc in the demo tenant, created by external-entra-id#43. Ignored while
    # entra_tenant_id is unset, which is what makes that follow-up a single line.
    entra_audience = "02b04e09-4f7b-4724-963b-a4c10a6825f2"
    required_roles = ["app.read"]
  }

  rcc = {
    openapi_spec_path = "../contracts/try-it-now-sandbox.openapi.yml"
    expose_paths      = ["/scenarios"]
    merge_spec_path   = "../contracts/rcc-courthouses.openapi.yml"

    display_name = "Try It Now — Refdata Court Hearing Courthouses"
    path         = "amp/try/rcc"

    service_url = "https://amp-try-it-now.demo.platform.hmcts.net/rcc"

    subscription_required = true

    # amp-api-try-rcc in the demo tenant, created by external-entra-id#43.
    entra_audience = "640c325d-5a0e-48ce-a616-710d0785f2cb"
    required_roles = ["app.read"]
  }
}
