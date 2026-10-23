locals {
  api_specs = {
    for api_key, api in var.apis :
    api_key => yamldecode(file(api.openapi_spec_path))
  }

  # The demonstrated API's own contract, where one is configured.
  #
  # Keyed only where merge_spec_path is set. A null-valued entry would force every later lookup
  # through a conditional, and HCL will not unify {} with a populated object.
  merge_specs = {
    for api_key, api in var.apis :
    api_key => yamldecode(file(api.merge_spec_path))
    if api.merge_spec_path != null
  }

  # What APIM actually publishes: the allow-listed paths from the sandbox contract, plus every
  # path from the demonstrated one. Unset expose_paths means publish the lot.
  combined_paths = {
    for api_key, api in var.apis :
    api_key => merge(
      {
        for path, item in local.api_specs[api_key].paths : path => item
        if api.expose_paths == null || contains(coalesce(api.expose_paths, []), path)
      },
      try(local.merge_specs[api_key].paths, {})
    )
  }

  # Schemas from both specs.
  #
  # merge() resolves a collision by letting the second win, silently - it would publish a schema
  # under a name whose definition came from the other spec. Rather than verify non-overlap each
  # time an API is added, every schema in the sandbox contract is prefixed `Sandbox`, so a
  # collision cannot arise. ErrorResponse is the one that would otherwise bite: this engine's rcc
  # contract defines it, and so does nearly every other api-cp-* contract.
  combined_schemas = {
    for api_key, api in var.apis :
    api_key => merge(
      try(local.api_specs[api_key].components.schemas, {}),
      try(local.merge_specs[api_key].components.schemas, {})
    )
  }

  # Declared only in the copy imported into APIM, so the portal's Try it shows them as pre-filled
  # boxes. The specs themselves are untouched - the demonstrated one is a vendored copy of a
  # published tag and must stay identical to it below the provenance header.
  try_it_headers = [
    {
      name        = "Authorization"
      in          = "header"
      required    = true
      description = "Bearer token. In the Entra realm, obtain one from your own app registration scoped to this API's resource app."
      schema      = { type = "string", default = "Bearer <access_token>" }
    },
    {
      name     = "Accept"
      in       = "header"
      required = false
      schema   = { type = "string", default = "application/json" }
    },
  ]

  http_methods = ["get", "put", "post", "delete", "patch", "head", "options"]

  # info and servers come from the sandbox contract. APIM overrides the backend with service_url
  # anyway, so the demonstrated spec's templated server block is deliberately dropped - it names
  # a host that does not exist and a {version} variable nothing here substitutes.
  combined_specs = {
    for api_key, api in var.apis :
    api_key => merge(local.api_specs[api_key], {
      paths = local.combined_paths[api_key]
      components = merge(
        try(local.api_specs[api_key].components, {}),
        { schemas = local.combined_schemas[api_key] }
      )
    })
  }

  # Always applied, never branched. An `api.try_it_auth_headers ? x : y` here would be the same
  # trap as above: the two sides are objects of different shape and HCL refuses to unify them.
  # Filtering the header list instead keeps one code path - with the flag off the comprehension
  # yields [], concat is a no-op, and the document is unchanged.
  import_specs = {
    for api_key, api in var.apis :
    api_key => yamlencode(merge(local.combined_specs[api_key], {
      paths = {
        for path, item in local.combined_paths[api_key] : path => merge(
          { for key, value in item : key => value if !contains(local.http_methods, key) },
          {
            for method, operation in item : method => merge(operation, {
              parameters = concat(
                try(operation.parameters, []),
                [for header in local.try_it_headers : header if api.try_it_auth_headers],
              )
            }) if contains(local.http_methods, method)
          }
        )
      }
    }))
  }
}

module "apis" {
  for_each = var.apis

  source = "git::https://github.com/hmcts/cnp-module-api-mgmt-api.git?ref=master"

  api_mgmt_rg   = var.api_mgmt_rg
  api_mgmt_name = var.api_mgmt_name

  # One product per API. Keys are shared with apim_products, which a variable validation enforces.
  product_id = module.product[each.key].product_id

  revision = each.value.revision

  display_name = coalesce(
    try(each.value.display_name, null),
    try(local.api_specs[each.key].info.title, null),
    each.key
  )

  # Derived from the short code, NOT from a spec title.
  #
  # try-slc derived it from info.title, which worked because its one spec had one title. Here
  # api_specs is the sandbox contract - the same file for every API - so a title-derived name
  # would be "try-it-now---sandbox-discovery" for all of them, and the second API registered in
  # this APIM instance would collide with the first. The short code is the one thing guaranteed
  # unique across the registry.
  name = coalesce(
    try(each.value.name, null),
    "try-it-now-${each.key}"
  )

  path = coalesce(
    try(each.value.path, null),
    lower(replace(each.key, "/[^0-9A-Za-z-]/", "-"))
  )

  protocols             = each.value.protocols
  service_url           = each.value.service_url
  subscription_required = each.value.subscription_required
  api_type              = each.value.api_type

  swagger_url    = local.import_specs[each.key]
  content_format = "openapi"
}

# The policy carries a validate-jwt block only where entra_tenant_id is set. policies/api-policy.xml
# explains why it is per-environment, why /scenarios is exempt, and why the throughput keys are
# per-API rather than per-deployment.
resource "azurerm_api_management_api_policy" "api_policy" {
  for_each = var.apis

  api_name            = module.apis[each.key].name
  api_management_name = var.api_mgmt_name
  resource_group_name = var.api_mgmt_rg

  xml_content = templatefile("${path.module}/policies/api-policy.xml", {
    entra_tenant_id = var.entra_tenant_id
    entra_audience  = each.value.entra_audience
    required_roles  = each.value.required_roles
    counter_key     = "try-${each.key}"
  })

  depends_on = [module.apis]
}
