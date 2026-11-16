api_mgmt_rg   = "rg-sps-platform-prod"
api_mgmt_name = "sps-api-mgmt-prod"

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

  pcd = {
    name = "try-it-now-cp-crime-prosecution-case-details"

    subscription_required         = true
    subscriptions_limit           = 20
    approval_required             = false
    published                     = true
    product_access_control_groups = ["developers", "administrators", "guests"]
  }

  dl = {
    name = "try-it-now-cp-crime-defendant-details"

    subscription_required         = true
    subscriptions_limit           = 20
    approval_required             = false
    published                     = true
    product_access_control_groups = ["developers", "administrators", "guests"]
  }
}

entra_tenant_id = "0d0315f6-3c04-4edb-ac37-b05f580cc122"

apis = {
  slc = {
    openapi_spec_path     = "../contracts/try-it-now-sandbox.openapi.yml"
    expose_paths          = ["/scenarios"]
    merge_spec_path       = "../contracts/slc-courtschedule.openapi.yml"
    display_name          = "Try It Now — Crime Scheduling and Listing Court Schedule"
    path                  = "amp/try/slc"
    service_url           = "https://amp-try-it-now.platform.hmcts.net/slc"
    subscription_required = true
    entra_audience        = "cabebf93-f8bd-42c1-9fd2-0fff1bd13177"
    required_roles        = ["app.read"]
    try_it_auth_headers   = true
  }

  rcc = {
    openapi_spec_path     = "../contracts/try-it-now-sandbox.openapi.yml"
    expose_paths          = ["/scenarios"]
    merge_spec_path       = "../contracts/rcc-courthouses.openapi.yml"
    display_name          = "Try It Now — Refdata Court Hearing Courthouses"
    path                  = "amp/try/rcc"
    service_url           = "https://amp-try-it-now.platform.hmcts.net/rcc"
    subscription_required = true
    entra_audience        = "38852e1f-a8b9-463f-bb9e-73e9c166a1ae"
    required_roles        = ["app.read"]
    try_it_auth_headers   = true
  }

  pcd = {
    openapi_spec_path = "../contracts/try-it-now-sandbox.openapi.yml"
    expose_paths      = ["/scenarios"]
    merge_spec_path   = "../contracts/pcd-case-details.openapi.yml"

    display_name = "Try It Now — Crime Prosecution Case Details"
    path         = "amp/try/pcd"

    service_url = "https://amp-try-it-now.platform.hmcts.net/pcd"

    subscription_required = true
    entra_audience        = "d9478881-b17f-40ef-a1b3-57728323430c"
    required_roles        = ["app.read"]
    try_it_auth_headers   = true
  }

  dl = {
    openapi_spec_path = "../contracts/try-it-now-sandbox.openapi.yml"
    expose_paths      = ["/scenarios"]
    merge_spec_path   = "../contracts/dl-defendant-details.openapi.yml"

    display_name = "Try It Now — Crime Defendant Details"
    path         = "amp/try/dl"

    service_url = "https://amp-try-it-now.platform.hmcts.net/dl"

    subscription_required = true
    entra_audience        = "7bffe402-e979-443e-8703-7b5a4e90d5c0"
    required_roles        = ["app.read"]
    try_it_auth_headers   = true
  }
}
