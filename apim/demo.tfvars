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

entra_tenant_id = "db827888-77df-4707-b3d9-24a3dfe32889"

apis = {
  slc = {
    openapi_spec_path = "../contracts/try-it-now-sandbox.openapi.yml"
    expose_paths      = ["/scenarios"]
    merge_spec_path   = "../contracts/slc-courtschedule.openapi.yml"
    display_name = "Try It Now — Crime Scheduling and Listing Court Schedule"
    path         = "amp/try/slc"
    service_url = "https://amp-try-it-now.demo.platform.hmcts.net/slc"
    subscription_required = true
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
    entra_audience = "640c325d-5a0e-48ce-a616-710d0785f2cb"
    required_roles = ["app.read"]
  }
}
