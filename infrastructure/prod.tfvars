# The subscription holding cft-prod-vnet, which the postgres_network provider reads the
# delegated subnet from. Not the one the resources are created in.
aks_subscription_id = "8cbc6f36-7c56-4963-9d36-739db5d00b27"

# amp-prod-mi is created by service-api-marketplace, so this repo reads it.
create_managed_identity = false
