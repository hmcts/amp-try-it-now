# Adopting amp-${env}-mi from try-cp-crime-schedulingandlisting-courtschedule.
#
# The identity already exists in every environment - try-slc created it, being amp's first
# component. Declaring it in main.tf without these blocks plans a CREATE, which fails on a name
# that is already taken. Importing moves ownership between two Terraform states and changes nothing
# in Azure.
#
# ORDER. try-cp-...#59 releases it with destroy = false, which takes it out of that state without
# touching the resource. Either order works - two states only conflict when both apply, and that
# one no longer declares it after its apply - but releasing first is tidier.
#
# These are idempotent and stay in place once they have run, the same as apim/imports.tf.
locals {
  # Keyed by environment, because the identity is per environment and so is the subscription it
  # sits in. sandbox is in a different one from aat and demo.
  amp_identity_ids = {
    "sandbox" = "/subscriptions/bf308a5c-0624-4334-8ff8-8dca9fd43783/resourceGroups/managed-identities-sandbox-rg/providers/Microsoft.ManagedIdentity/userAssignedIdentities/amp-sandbox-mi"
    "aat"     = "/subscriptions/1c4f0704-a29e-403d-b719-b90c34ef14c9/resourceGroups/managed-identities-aat-rg/providers/Microsoft.ManagedIdentity/userAssignedIdentities/amp-aat-mi"
    "demo"    = "/subscriptions/1c4f0704-a29e-403d-b719-b90c34ef14c9/resourceGroups/managed-identities-demo-rg/providers/Microsoft.ManagedIdentity/userAssignedIdentities/amp-demo-mi"
  }

  # Empty in any environment not listed, so this is inert rather than an error there. An import
  # block naming an id that does not resolve fails at plan.
  adopt_amp_identity = contains(keys(local.amp_identity_ids), var.env) ? toset([var.env]) : toset([])
}

import {
  for_each = local.adopt_amp_identity
  to       = azurerm_user_assigned_identity.amp
  id       = local.amp_identity_ids[each.value]
}
