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

entra_tenant_id = "db827888-77df-4707-b3d9-24a3dfe32889"

apis = {
  slc = {
    openapi_spec_path     = "../contracts/try-it-now-sandbox.openapi.yml"
    expose_paths          = ["/scenarios"]
    merge_spec_path       = "../contracts/slc-courtschedule.openapi.yml"
    display_name          = "Try It Now — Crime Scheduling and Listing Court Schedule"
    path                  = "amp/try/slc"
    service_url           = "https://amp-try-it-now.demo.platform.hmcts.net/slc"
    subscription_required = true
    entra_audience        = "02b04e09-4f7b-4724-963b-a4c10a6825f2"
    required_roles        = ["app.read"]
  }

  rcc = {
    openapi_spec_path     = "../contracts/try-it-now-sandbox.openapi.yml"
    expose_paths          = ["/scenarios"]
    merge_spec_path       = "../contracts/rcc-courthouses.openapi.yml"
    display_name          = "Try It Now — Refdata Court Hearing Courthouses"
    path                  = "amp/try/rcc"
    service_url           = "https://amp-try-it-now.demo.platform.hmcts.net/rcc"
    subscription_required = true
    entra_audience        = "640c325d-5a0e-48ce-a616-710d0785f2cb"
    required_roles        = ["app.read"]
    try_it_auth_headers   = true
  }

  pcd = {
    openapi_spec_path = "../contracts/try-it-now-sandbox.openapi.yml"
    expose_paths      = ["/scenarios"]
    merge_spec_path   = "../contracts/pcd-case-details.openapi.yml"

    display_name = "Try It Now — Crime Prosecution Case Details"
    path         = "amp/try/pcd"

    service_url = "https://amp-try-it-now.demo.platform.hmcts.net/pcd"

    subscription_required = true

    # amp-api-try-pcd, created by external-entra-id#48.
    entra_audience = "bb45396e-c703-4afc-b6c9-05aa1fe18dba"
    required_roles = ["app.read"]
  }

  dl = {
    openapi_spec_path = "../contracts/try-it-now-sandbox.openapi.yml"
    expose_paths      = ["/scenarios"]
    merge_spec_path   = "../contracts/dl-defendant-details.openapi.yml"

    display_name = "Try It Now — Crime Defendant Details"
    path         = "amp/try/dl"

    service_url = "https://amp-try-it-now.demo.platform.hmcts.net/dl"

    subscription_required = true

    # amp-api-try-dl, created by external-entra-id#52.
    entra_audience = "f78066dd-0406-465b-9d9e-6073f5f8925b"
    required_roles = ["app.read"]
  }
}
