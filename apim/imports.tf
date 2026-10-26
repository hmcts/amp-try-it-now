# APIM adds the administrators group to a product the moment it is created, before Terraform gets
# to manage that association. So the first apply creates the product, then fails on the group it
# did not create:
#
#   Error: a resource with the ID ".../products/try-it-now-cp-refdata-courthearing/groups/
#   administrators" already exists - to be managed via Terraform this resource needs to be
#   imported into the State
#
# Importing is the fix rather than dropping "administrators" from product_access_control_groups:
# the group genuinely should be on the product, and leaving it out of the config would mean
# Terraform never managed something it is responsible for.
#
# THE MAP IS EMPTY, AND HAS TO BE ON THE FIRST APPLY. An import block naming an object that does
# not exist yet fails at plan, so the entry cannot be written ahead of time - the product has to
# exist before its group can be imported. The sequence is: apply (creates the product, fails on
# the group), add the entry below, apply again (converges). Two applies, once per environment,
# and the second is a one-line change.
#
# Keyed on short code then APIM instance, because this configuration registers one product per
# API and each gets its own group.
#
# The sbox entry below is the first apply's error message, copied verbatim.
locals {
  administrators_product_group_ids = {
    "rcc" = {
      "sps-api-mgmt-sbox" = "/subscriptions/bd2864ed-4f3e-45ed-9c6a-8d179674bab1/resourceGroups/rg-sps-platform-sbox/providers/Microsoft.ApiManagement/service/sps-api-mgmt-sbox/products/try-it-now-cp-refdata-courthearing/groups/administrators"
    }
  }

  administrators_to_import = toset([
    for key, by_instance in local.administrators_product_group_ids :
    key if contains(keys(by_instance), var.api_mgmt_name)
  ])
}

import {
  for_each = local.administrators_to_import
  to       = module.product[each.value].azurerm_api_management_product_group.access_control_groups["administrators"]
  id       = local.administrators_product_group_ids[each.value][var.api_mgmt_name]
}
