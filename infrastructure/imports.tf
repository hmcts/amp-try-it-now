# Adopting amp-${env}-mi from try-cp-crime-schedulingandlisting-courtschedule, which created it and
# is being retired (try-cp-...#59 releases it, destroy = false). Without these the resource in
# main.tf plans a create and fails on a name that already exists.
#
# Idempotent, and left in place once run - same as apim/imports.tf.
locals {
  # sandbox sits in a different subscription from aat and demo, so these are listed rather than
  # built from a template.
  amp_identity_ids = {
    "sandbox" = "/subscriptions/bf308a5c-0624-4334-8ff8-8dca9fd43783/resourceGroups/managed-identities-sandbox-rg/providers/Microsoft.ManagedIdentity/userAssignedIdentities/amp-sandbox-mi"
    "aat"     = "/subscriptions/1c4f0704-a29e-403d-b719-b90c34ef14c9/resourceGroups/managed-identities-aat-rg/providers/Microsoft.ManagedIdentity/userAssignedIdentities/amp-aat-mi"
    "demo"    = "/subscriptions/1c4f0704-a29e-403d-b719-b90c34ef14c9/resourceGroups/managed-identities-demo-rg/providers/Microsoft.ManagedIdentity/userAssignedIdentities/amp-demo-mi"
  }

  adopt_amp_identity = contains(keys(local.amp_identity_ids), var.env) ? toset([var.env]) : toset([])
}

import {
  for_each = local.adopt_amp_identity
  to       = azurerm_user_assigned_identity.amp
  id       = local.amp_identity_ids[each.value]
}
