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
#   sbox     try-cp-crime-schedulingandlisting-courtschedule   DTS AMp Sandbox
#   aat      api-cp-crime-schedulingandlisting-courtschedule   DTS AMp Staging   <- note the name
#   preview  api-cp-crime-schedulingandlisting-courtschedule   DTS AMp Preview
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
# Demo is deliberately absent: its appId is not recorded in any repo and its subscription name is
# not derivable (aat is DTS-SPS-STG, not DTS-SPS-AAT), so both are a PlatOps ask. Try It Now
# stops at demo - there is no prod set and there must not be one.
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

# Inert until apim/preview.tfvars exists.
set_env_vars PREVIEW \
  bfcb14a6-cf19-4bdc-93b7-4f32da62b23e \
  7cfd7e05-06a1-4d9b-a426-db304bc99aab \
  DTS-SPS-PREVIEW

echo
echo "--- now set on ${REPO} ---"
gh api "/repos/${REPO}/actions/variables" --jq '.variables[] | "\(.name) = \(.value)"' | sort
