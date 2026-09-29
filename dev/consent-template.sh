#!/bin/bash -e
# Loads the MII consent policies used by docs/examples/data_request.json and a
# matching consent template (dev/consent-template.json) into the test Mainzelliste.
# Usage: dev/consent-template.sh [mainzelliste url] [admin api key]

SD=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" &>/dev/null && pwd)

ML_URL=${1:-http://localhost:8082}
API_KEY=${2:-${ML_ADMIN_PASSPHRASE:-admin-password}}
POLICY_SET=MiiConsentPolicyCodeSystem-1.0.2

ml() {
  curl -sS --fail-with-body -H "mainzellisteApiKey: $API_KEY" -H "mainzellisteApiVersion: 3.2" "$@"
}

token() {
  ml -X POST -H "Content-Type: application/json" \
    -d "{\"type\": \"$1\", \"allowedUses\": \"${2:-1}\"}" \
    "$ML_URL/sessions/$SESSION_ID/tokens" | jq -er ".id // .tokenId"
}

SESSION_ID=$(ml -X POST "$ML_URL/sessions" | jq -er .sessionId)

ml -H "Content-Type: application/json" \
  -H "Authorization: MainzellisteToken $(token addConsentPolicySet)" \
  -d "{\"id\": \"$POLICY_SET\", \"name\": \"Mii Consent Policy - 1.0.2\", \"externalId\": \"urn:oid:2.16.840.1.113883.3.1937.777.24.5.3\"}" \
  "$ML_URL/consent-policies" >/dev/null

POLICIES=$(jq -c '.contained[0].provision.provision[].code[0].coding[0] | {code, text: .display}' "$SD/consent-template.json")
POLICY_TOKEN=$(token addConsentPolicy "$(echo "$POLICIES" | wc -l)")
while read -r policy; do
  ml -H "Content-Type: application/json" -H "Authorization: MainzellisteToken $POLICY_TOKEN" \
    -d "$policy" "$ML_URL/consent-policies/$POLICY_SET/policy" >/dev/null
done <<< "$POLICIES"

ml -H "Content-Type: application/fhir+json" \
  -H "Authorization: MainzellisteToken $(token addConsentTemplate)" \
  --data @"$SD/consent-template.json" "$ML_URL/fhir/Questionnaire" >/dev/null

echo "Loaded consent template $(jq -r '.identifier[0].value' "$SD/consent-template.json") into $ML_URL"
