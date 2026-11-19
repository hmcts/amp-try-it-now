# Own workspace: terraform-module-application-insights always uses the shared hmcts-<env> one, readable by every CNP team.
resource "azurerm_log_analytics_workspace" "this" {
  name                = "${var.product}-${var.component}-${var.env}"
  location            = var.location
  resource_group_name = azurerm_resource_group.rg.name
  sku                 = "PerGB2018"
  retention_in_days   = var.log_analytics_retention_days
  daily_quota_gb      = var.log_analytics_daily_quota_gb
  tags                = local.tags
}

resource "azurerm_application_insights" "this" {
  name                = "${var.product}-${var.component}-${var.env}"
  location            = var.location
  resource_group_name = azurerm_resource_group.rg.name
  application_type    = "java"
  workspace_id        = azurerm_log_analytics_workspace.this.id
  tags                = local.tags
}

# Whole agent config as one secret, so each environment points APPLICATIONINSIGHTS_CONFIGURATION_FILE at its own mount path.
resource "azurerm_key_vault_secret" "app_insights_config" {
  name = "app-insights-config"
  value = jsonencode({
    connectionString = azurerm_application_insights.this.connection_string
    role             = { name = "${var.product}-${var.component}" }
    sampling = {
      percentage = 100
      overrides = [{
        telemetryType = "request"
        attributes    = [{ key = "http.url", value = "https?://[^/]+/health.*", matchType = "regexp" }]
        percentage    = 1
      }]
    }
  })
  key_vault_id = module.vault.key_vault_id
  depends_on   = [module.vault]
}
