# Sandbox as a product on the REAL API, not a second API.
#
# A consumer calls the producer's own API path with a key from this product; the product policy
# validates the sandbox token and sends the call to this engine. The producer's API definition is
# untouched; its policy only needs a gated branch that skips its own token check for this product.
#
# Created unpublished: invisible in the portal until `published` is flipped, so it can be tested
# with an admin-created subscription without affecting anyone.

variable "sandbox_products" {
  description = "Sandbox products attached to real producer APIs, keyed by the API's short code."
  type = map(object({
    product_id          = string
    display_name        = string
    real_api_name       = string
    backend_url         = string
    entra_audience      = string
    required_roles      = list(string)
    subscriptions_limit = optional(number, 20)
    published           = optional(bool, false)
  }))
  default = {}
}

resource "azurerm_api_management_product" "sandbox" {
  for_each = var.sandbox_products

  product_id          = each.value.product_id
  api_management_name = var.api_mgmt_name
  resource_group_name = var.api_mgmt_rg

  display_name          = each.value.display_name
  description           = "Sandbox for ${each.value.real_api_name}: recorded scenarios, no real data."
  subscription_required = true
  approval_required     = false
  subscriptions_limit   = each.value.subscriptions_limit
  published             = each.value.published
}

resource "azurerm_api_management_product_policy" "sandbox" {
  for_each = var.sandbox_products

  product_id          = azurerm_api_management_product.sandbox[each.key].product_id
  api_management_name = var.api_mgmt_name
  resource_group_name = var.api_mgmt_rg

  xml_content = templatefile("${path.module}/policies/sandbox-product-policy.xml", {
    entra_tenant_id = var.entra_tenant_id
    entra_audience  = each.value.entra_audience
    required_roles  = each.value.required_roles
    backend_url     = each.value.backend_url
  })
}

# Linked last, so the product never reaches the API without its policy in place.
resource "azurerm_api_management_product_api" "sandbox" {
  for_each = var.sandbox_products

  product_id          = azurerm_api_management_product.sandbox[each.key].product_id
  api_name            = each.value.real_api_name
  api_management_name = var.api_mgmt_name
  resource_group_name = var.api_mgmt_rg

  depends_on = [azurerm_api_management_product_policy.sandbox]
}
