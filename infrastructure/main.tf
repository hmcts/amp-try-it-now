provider "azurerm" {
  features {}
}

provider "azurerm" {
  features {}
  skip_provider_registration = true
  alias                      = "postgres_network"
  subscription_id            = var.aks_subscription_id
}

locals {
  tags = merge(
    var.common_tags,
    tomap({ "Team Contact" = var.team_contact })
  )
}

data "azurerm_user_assigned_identity" "jenkins" {
  name                = "jenkins-${var.env == "sandbox" ? "sbox" : var.env}-mi"
  resource_group_name = "managed-identities-${var.env}-rg"
}

# The product-wide identity the pods run as - amp-sandbox-mi, amp-aat-mi, amp-demo-mi. Looked up,
# never created: try-cp-crime-schedulingandlisting-courtschedule creates it, being amp's first
# component, and the name carries no component segment so a second creator collides.
#
# No sandbox special case, unlike jenkins above: this one really is amp-sandbox-mi.
data "azurerm_user_assigned_identity" "amp" {
  name                = "${var.product}-${var.env}-mi"
  resource_group_name = "managed-identities-${var.env}-rg"
}

# Resource group and Key Vault are COMPONENT-scoped, following try-slc.
#
# Product-scoped names ("${var.product}-shared-${var.env}") cannot work here: every amp repo builds
# the same name from the same expression, so the second Terraform state to apply declares resources
# that already exist and fails. One vault per component removes the clash and lets each repo own
# its own state.
resource "azurerm_resource_group" "rg" {
  name     = "${var.product}-${var.component}-${var.env}"
  location = var.location
  tags     = local.tags
}

# create_managed_identity is FALSE here, and that is the important line - but on its own it also
# granted the identity nothing, which is the bug this fixes. managed_identity_object_id above is
# what puts amp-{env}-mi into the vault's access policies without creating it.
#
# cnp-module-key-vault names the identity "${var.product}-${local.env}-mi" with no component
# segment, so it is product-wide by construction - amp-sandbox-mi, amp-aat-mi. There is one per
# namespace and it is what the Flux workload-identity service account federates against.
# try-cp-crime-schedulingandlisting-courtschedule creates it, being amp's first component. Any
# other amp repo setting this true collides on the name.
module "vault" {
  source                               = "git@github.com:hmcts/cnp-module-key-vault?ref=master"
  name                                 = var.vault_name != "" ? var.vault_name : "${var.product}-${var.component}-${var.env}"
  product                              = var.product
  env                                  = var.env
  tenant_id                            = var.tenant_id
  object_id                            = var.jenkins_AAD_objectId
  resource_group_name                  = azurerm_resource_group.rg.name
  product_group_name                   = "DTS API Marketplace"
  common_tags                          = local.tags
  managed_identity_object_id           = data.azurerm_user_assigned_identity.amp.principal_id
  create_managed_identity              = false
  additional_managed_identities_access = var.additional_managed_identities_access
  jenkins_object_id                    = data.azurerm_user_assigned_identity.jenkins.principal_id
}

# A server of its own, for the same reason the vault is component-scoped: two repositories running
# terraform against one server is a state conflict, and whichever applies second sees the other's
# databases as drift. The name must differ from both "${var.product}-flexible" and try-slc's
# "${var.product}-try-flexible".
module "postgresql_flexible" {
  providers = {
    azurerm.postgres_network = azurerm.postgres_network
  }

  source                    = "git@github.com:hmcts/terraform-module-postgresql-flexible?ref=master"
  env                       = var.env
  product                   = var.product
  name                      = "${var.product}-tin-flexible"
  component                 = var.component
  business_area             = "CFT"
  location                  = var.location
  subnet_suffix             = var.pgsql_subnet_suffix
  public_access             = var.pgsql_public_access
  pgsql_delegated_subnet_id = var.pgsql_public_access ? "bypass" : ""

  common_tags          = local.tags
  admin_user_object_id = var.jenkins_AAD_objectId

  pgsql_databases = [
    { name : "tryitnow" }
  ]

  pgsql_version = "16"
  pgsql_sku     = var.pgsql_sku
}

# Prefixed with the component so they never collide with another amp repo's secrets, and mapped
# onto the POSTGRES_* aliases application.yaml reads by the chart's keyVaults block.
resource "azurerm_key_vault_secret" "postgres_user" {
  name         = "try-it-now-POSTGRES-USER"
  value        = module.postgresql_flexible.username
  key_vault_id = module.vault.key_vault_id
  depends_on   = [module.vault]
}

resource "azurerm_key_vault_secret" "postgres_pass" {
  name         = "try-it-now-POSTGRES-PASS"
  value        = module.postgresql_flexible.password
  key_vault_id = module.vault.key_vault_id
  depends_on   = [module.vault]
}

resource "azurerm_key_vault_secret" "postgres_host" {
  name         = "try-it-now-POSTGRES-HOST"
  value        = module.postgresql_flexible.fqdn
  key_vault_id = module.vault.key_vault_id
  depends_on   = [module.vault]
}

resource "azurerm_key_vault_secret" "postgres_port" {
  name         = "try-it-now-POSTGRES-PORT"
  value        = "5432"
  key_vault_id = module.vault.key_vault_id
  depends_on   = [module.vault]
}

resource "azurerm_key_vault_secret" "postgres_database" {
  name         = "try-it-now-POSTGRES-DATABASE"
  value        = "tryitnow"
  key_vault_id = module.vault.key_vault_id
  depends_on   = [module.vault]
}
