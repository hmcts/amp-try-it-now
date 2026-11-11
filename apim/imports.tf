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
      "sps-api-mgmt-demo" = "/subscriptions/058bf954-ac1f-462b-bca4-42a30314bd74/resourceGroups/rg-sps-platform-demo/providers/Microsoft.ApiManagement/service/sps-api-mgmt-demo/products/try-it-now-cp-refdata-courthearing/groups/administrators"
    }

    # demo only. slc is created here rather than adopted - try-slc never registered in demo - so it
    # meets the same group APIM attaches on creation that rcc met in sbox. The sbox instance is
    # absent from this map on purpose: there, slc's groups arrive through adopt_slc_groups below.
    "slc" = {
      "sps-api-mgmt-demo" = "/subscriptions/058bf954-ac1f-462b-bca4-42a30314bd74/resourceGroups/rg-sps-platform-demo/providers/Microsoft.ApiManagement/service/sps-api-mgmt-demo/products/try-it-now-cp-crime-schedulingandlisting/groups/administrators"
    }

    "pcd" = {
      "sps-api-mgmt-sbox" = "/subscriptions/bd2864ed-4f3e-45ed-9c6a-8d179674bab1/resourceGroups/rg-sps-platform-sbox/providers/Microsoft.ApiManagement/service/sps-api-mgmt-sbox/products/try-it-now-cp-crime-prosecution-case-details/groups/administrators"
      "sps-api-mgmt-demo" = "/subscriptions/058bf954-ac1f-462b-bca4-42a30314bd74/resourceGroups/rg-sps-platform-demo/providers/Microsoft.ApiManagement/service/sps-api-mgmt-demo/products/try-it-now-cp-crime-prosecution-case-details/groups/administrators"
    }

    "dl" = {
      "sps-api-mgmt-sbox" = "/subscriptions/bd2864ed-4f3e-45ed-9c6a-8d179674bab1/resourceGroups/rg-sps-platform-sbox/providers/Microsoft.ApiManagement/service/sps-api-mgmt-sbox/products/try-it-now-cp-crime-defendant-details/groups/administrators"
      "sps-api-mgmt-demo" = "/subscriptions/058bf954-ac1f-462b-bca4-42a30314bd74/resourceGroups/rg-sps-platform-demo/providers/Microsoft.ApiManagement/service/sps-api-mgmt-demo/products/try-it-now-cp-crime-defendant-details/groups/administrators"
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

# ─── Adopting the slc product and API from try-slc ───────────────────────────────────────────
#
# These are not first-apply workarounds like the block above. They move an existing registration
# between two Terraform states without touching Azure: try-slc releases it with `removed` blocks
# (its apim/ PR, which must apply first), and this adopts it.
#
# WHY NOT DELETE AND RECREATE. The product carries 19 live subscriptions. Subscriptions belong to
# the product, so destroying it invalidates every key and every consumer has to re-subscribe
# through the Marketplace. Importing keeps all of them, and the only thing that changes in Azure
# is the backend URL - which is the point of the exercise.
#
# ORDERING. try-slc's release must apply before this. Two states managing one resource is only a
# problem when both apply, and after the release try-slc's configuration no longer names them.
#
# These blocks are idempotent and are left in place after they have run, the same as the
# administrators import above - which has been in this file since 26 October and has been through
# several applies since.
locals {
  # Keyed by APIM instance, so this is inert in any environment that has not adopted anything.
  # sbox is the only environment try-slc ever registered, and the only tfvars this repository has.
  slc_apim_scopes = {
    "sps-api-mgmt-sbox" = "/subscriptions/bd2864ed-4f3e-45ed-9c6a-8d179674bab1/resourceGroups/rg-sps-platform-sbox/providers/Microsoft.ApiManagement/service/sps-api-mgmt-sbox"
  }

  slc_apim_scope = try(local.slc_apim_scopes[var.api_mgmt_name], null)

  # One element where this instance has something to adopt, none otherwise. An import block whose
  # `to` names an instance the configuration does not declare fails at plan, so the gate has to be
  # here rather than in a comment.
  adopt_slc        = local.slc_apim_scope == null ? toset([]) : toset(["slc"])
  adopt_slc_groups = local.slc_apim_scope == null ? toset([]) : toset(["administrators", "developers", "guests"])

  # try-slc's product id, which the tfvars repeats. Spelled once here so a typo cannot make the
  # import and the configuration disagree - that failure mode plans as a create-and-destroy, not an
  # error.
  slc_product_id = "try-it-now-cp-crime-schedulingandlisting"
}

import {
  for_each = local.adopt_slc
  to       = module.product[each.value].azurerm_api_management_product.product
  id       = "${local.slc_apim_scope}/products/${local.slc_product_id}"
}

# All three, unlike rcc's single administrators entry: rcc created its own groups and needed only
# the one APIM had attached behind Terraform's back. None of these three was created from here.
import {
  for_each = local.adopt_slc_groups
  to       = module.product["slc"].azurerm_api_management_product_group.access_control_groups[each.value]
  id       = "${local.slc_apim_scope}/products/${local.slc_product_id}/groups/${each.value}"
}

# The API, its product link and its policy were adopted here too, under try-slc's API name
# try-it-now---public-api. They are no longer imported: the API is now named try-it-now-slc like
# every other one, which APIM can only do by recreating it, so those three are replaced rather
# than adopted. The product and its groups above are untouched, and with them the subscriptions.
