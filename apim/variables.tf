# ─── Variables injected automatically by the HMCTS GitHub Actions reusable workflow ───
#
# terraform-deploy-openid passes these with -var regardless of whether the configuration uses
# them, so they must be declared or every plan fails on an undeclared variable.

variable "env" {
  type        = string
  description = "Environment name, injected by CI (e.g. sbox, aat)."
  default     = ""
}

variable "product" {
  type        = string
  description = "Product name, injected by CI."
  default     = ""
}

variable "subscription" {
  type        = string
  description = "Azure subscription ID, injected by CI."
  default     = ""
}

variable "aks_subscription_id" {
  type        = string
  description = "AKS subscription ID, injected by CI."
  default     = ""
}

variable "tenant_id" {
  type        = string
  description = "Azure tenant ID, injected by CI."
  default     = ""
}

variable "builtFrom" {
  type        = string
  description = "Repository reference, injected by CI."
  default     = ""
}

variable "ci_service_principal_object_id" {
  type        = string
  description = "CI service principal object ID, injected by CI."
  default     = ""
}

# ─── Module-specific variables ───

variable "api_mgmt_rg" {
  type        = string
  description = "Resource group name of the target APIM instance."
}

variable "api_mgmt_name" {
  type        = string
  description = "Name of the target APIM instance."
}

# One product per API, not one per deployment.
#
# try-slc registered a single product because it was a single-API service. This engine serves a
# sandbox for every registered API from one deployment, and a product is what a consumer
# subscribes to: a shared product would hand one subscription key access to every sandbox, and
# would put every API's rate limit in one bucket. The map is keyed by the API's short code, the
# same key `apis` uses and the same one src/main/resources/registry/apis.yaml declares.
variable "apim_products" {
  description = "Products to create in APIM, keyed by the API's short code."
  type = map(object({
    name                          = string
    description                   = optional(string)
    subscription_required         = optional(bool, true)
    subscriptions_limit           = optional(number, 20)
    approval_required             = optional(bool, true)
    published                     = optional(bool, true)
    product_access_control_groups = optional(list(string), [])
  }))
}

# ─── Entra validation (per environment) ───
#
# Set these and every API policy gains a validate-jwt block; leave them unset and none does.
#
# They are per-environment because auth.key-source is, and the two must agree. Under SELF_ISSUED
# the service signs tokens with its own key and carries its own issuer - Microsoft has never heard
# of them, so an Entra validate-jwt in front of it rejects every token it is handed. Under ENTRA
# the reverse: without validate-jwt the gateway forwards tokens it never checked, and the only
# thing standing between a caller and the sandbox is the service. See policies/api-policy.xml.
#
# entra_audience is per-API, because the audience is the access boundary between sandboxes. The
# roles are deliberately the same name on every API (app.read), so audience is the only thing in
# a token that says which sandbox it was minted for.

variable "entra_tenant_id" {
  type        = string
  description = "Entra tenant that issues tokens for this environment. Unset disables validate-jwt."
  default     = null
}

variable "apis" {
  description = "APIs to register in APIM, keyed by short code. Details come from the OpenAPI specs."
  type = map(object({
    openapi_spec_path = string

    # The full backend URL, not a host fragment.
    #
    # It must include the API's short-code prefix. The engine routes every request through
    # @RequestMapping("/{shortCode}/**") and resolves the API from the first path segment, so a
    # bare host here sends /courthouses/{id} to a service that has no such path - every operation
    # 404s, and it reads as a backend fault rather than a configuration one.
    #
    # APIM appends the operation template to this URL, so the specs keep their own paths verbatim
    # and the prefix is invisible to the consumer.
    service_url = string

    name         = optional(string)
    display_name = optional(string)
    path         = optional(string)
    revision     = optional(string, "1")
    protocols    = optional(list(string), ["https"])
    api_type     = optional(string, "http")

    # Whether APIM demands an Ocp-Apim-Subscription-Key.
    subscription_required = optional(bool, false)

    # This API's Entra resource app id: the bare client ID GUID, not api://<guid>. It must equal
    # what the pod validates against - the engine reads it from the variable named by audienceEnv
    # in the registry. If the two drift, a token fails at one layer or the other and the two 401s
    # look identical from outside. Ignored unless entra_tenant_id is set.
    entra_audience = optional(string)

    # Roles accepted by the gateway. Ignored unless entra_tenant_id is set. The role check that
    # decides an actual request lives in AuthorizationPolicy, not here.
    required_roles = optional(list(string), ["app.read"])

    # Adds Authorization and Accept header boxes to the portal's Try it (APIM import only).
    try_it_auth_headers = optional(bool, false)

    # Paths from openapi_spec_path to publish. Unset publishes all of them.
    expose_paths = optional(list(string))

    # A second spec whose paths and schemas are merged into the imported document. This is how the
    # API being demonstrated reaches APIM, so a consumer sees one API with the discovery endpoint
    # and the real shape together rather than two half-APIs.
    merge_spec_path = optional(string)
  }))

  validation {
    condition     = alltrue([for api in values(var.apis) : length(api.required_roles) > 0])
    error_message = "Each API must have at least one required_roles entry."
  }

  validation {
    condition     = length(setsubtract(keys(var.apis), keys(var.apim_products))) == 0
    error_message = "Every key in apis must have a matching key in apim_products."
  }

  # Caught here rather than at the gateway. A null audience renders an empty <audience/> element,
  # which APIM accepts and which then matches nothing - so every call 401s with a message about
  # token validation and nothing points at the missing value.
  validation {
    condition = var.entra_tenant_id == null || alltrue([
      for api in values(var.apis) : try(length(api.entra_audience) > 0, false)
    ])
    error_message = "entra_tenant_id is set, so every API must also set entra_audience."
  }
}
