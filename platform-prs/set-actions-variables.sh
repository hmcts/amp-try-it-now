#!/usr/bin/env bash
#
# Sets the per-environment Azure OIDC variables on hmcts/amp-try-it-now.
#
# These are IDS, NOT SECRETS. OIDC removes the long-lived credential, not the identifier - trust
# comes from Azure matching the token's subject against a federated credential, so the client id
# is safe in a repository variable and in this file.
#
# Values taken from repos already deploying to each environment, and cross-checked against the
# scopes on the matching app registration in azure-github-federation-config:
#
#   sbox   try-cp-crime-schedulingandlisting-courtschedule   DTS AMp Sandbox
#   aat    api-cp-crime-schedulingandlisting-courtschedule   DTS AMp Staging   <- note the name
#   demo   nothing has it                                    DTS AMp Demo      <- see below
#
# sbox -> aat -> demo is the whole path. Preview is deliberately not here: it is CNP's ephemeral
# per-PR environment, and publishing a product per PR into a shared APIM instance is not what
# this service wants.
#
# TWO THINGS THIS SCRIPT DOES NOT DO, and the variables are useless without them:
#
#   1. apim/<env>.tfvars must exist. The workflow builds its matrix from those filenames, so
#      until the file is there nothing reads these variables at all - they are inert, not wrong.
#   2. Two federated subjects per environment must be added to app-registrations.yaml in
#      hmcts/azure-github-federation-config, under that environment's DTS AMp <Env> registration.
#      With the variables set but no subject, login fails with an AADSTS error naming the subject
#      Azure rejected - a different failure from the empty-client-id one, and progress.
#
# DEMO IS INCOMPLETE ON PURPOSE. Its subscription id is known - taken from the scopes on the
# DTS AMp Demo app registration, the same way the sbox/aat/preview ids were derived and verified
# against their repo variables. The other two are not:
#
#   AZURE_CLIENT_ID_DEMO     the appId of DTS AMp Demo. Terraform creates it; it is in no file and
#                            no repo holds it, because nothing has ever deployed to demo.
#   AZURE_SUBSCRIPTION_DEMO  the subscription NAME. Do not guess - sbox is DTS-SPS-SBOX and
#                            preview is DTS-SPS-PREVIEW, but aat is DTS-SPS-STG, so the pattern
#                            does not hold.
#
# Both are a PlatOps ask. Fill them in below and the demo block runs; until then it skips itself
# rather than setting a placeholder that would fail later with a confusing error.
#
# Demo is the LAST environment. There is no prod set here and there must not be one.
#
# Idempotent: gh variable set overwrites.

set -euo pipefail

REPO="${REPO:-hmcts/amp-try-it-now}"

set_env_vars() {
  local env="$1" client_id="$2" subscription_id="$3" subscription="$4"
  echo "--- ${env} ---"
  gh variable set "AZURE_CLIENT_ID_${env}"       --repo "$REPO" --body "$client_id"
  gh variable set "AZURE_SUBSCRIPTION_ID_${env}" --repo "$REPO" --body "$subscription_id"
  gh variable set "AZURE_SUBSCRIPTION_${env}"    --repo "$REPO" --body "$subscription"
}

# The one that is currently blocking: apim/sbox.tfvars exists, so CI reads these today.
set_env_vars SBOX \
  72a7651d-9d2a-4c04-9085-957efeab0e53 \
  bd2864ed-4f3e-45ed-9c6a-8d179674bab1 \
  DTS-SPS-SBOX

# Inert until apim/aat.tfvars exists.
set_env_vars AAT \
  5c5c3162-75d8-4e48-a0af-4c35bdab3d0c \
  70bea6e3-384f-4cf4-b551-743a78d716cd \
  DTS-SPS-STG

# Inert until apim/demo.tfvars exists, and skipped entirely until PlatOps supply the two unknowns.
DEMO_CLIENT_ID="ASK-PLATOPS-appId-of-DTS-AMp-Demo"
DEMO_SUBSCRIPTION="ASK-PLATOPS-subscription-name"
DEMO_SUBSCRIPTION_ID="058bf954-ac1f-462b-bca4-42a30314bd74"   # known: from DTS AMp Demo's scopes

if [[ "$DEMO_CLIENT_ID" == ASK-PLATOPS-* || "$DEMO_SUBSCRIPTION" == ASK-PLATOPS-* ]]; then
  echo "--- DEMO: skipped, still waiting on PlatOps for the appId and subscription name ---"
else
  set_env_vars DEMO "$DEMO_CLIENT_ID" "$DEMO_SUBSCRIPTION_ID" "$DEMO_SUBSCRIPTION"
fi

echo
echo "--- now set on ${REPO} ---"
gh api "/repos/${REPO}/actions/variables" --jq '.variables[] | "\(.name) = \(.value)"' | sort
