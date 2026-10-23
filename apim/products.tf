locals {
  # The first paragraph of the demonstrated API's description, on one line, unless the product
  # overrides it. The demonstrated contract rather than the sandbox one: the sandbox contract
  # describes /scenarios, which is the same text for every product and would make every entry in
  # the Marketplace read identically.
  product_descriptions = {
    for key, product in var.apim_products :
    key => try(coalesce(
      product.description,
      replace(trimspace(split("\n\n", trimspace(
        local.merge_specs[key].info.description
      ))[0]), "\n", " ")
    ), null)
  }
}

module "product" {
  for_each = var.apim_products

  source = "git::https://github.com/hmcts/cnp-module-api-mgmt-product.git?ref=master"

  api_mgmt_rg                   = var.api_mgmt_rg
  api_mgmt_name                 = var.api_mgmt_name
  name                          = each.value.name
  description                   = local.product_descriptions[each.key]
  subscription_required         = each.value.subscription_required
  subscriptions_limit           = each.value.subscriptions_limit
  approval_required             = each.value.approval_required
  published                     = each.value.published
  product_access_control_groups = each.value.product_access_control_groups
  product_policy                = ""
}
