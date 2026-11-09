#!/usr/bin/env bash
set -euo pipefail

ROOT=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
PUSH_ROOT=$(cd -- "$ROOT/../mainzelliste-onkostar-push" && pwd)
TTP_ROOT=$(cd -- "$ROOT/../dhki-dkfz-ttp" && pwd)
BEAM_ROOT=$(cd -- "$ROOT/../beam-connect" && pwd)
COMPOSE=(docker compose -f "$ROOT/e2e/docker-compose.yml")
TMP=$(mktemp -d)

cleanup() {
  status=$?
  if [[ ${KEEP_E2E_STACK:-0} != 1 ]]; then
    "${COMPOSE[@]}" down -v --remove-orphans >/dev/null 2>&1 || true
    "$BEAM_ROOT/dev/start" stop >/dev/null 2>&1 || true
  else
    echo "Keeping E2E services running (KEEP_E2E_STACK=1)."
  fi
  rm -rf "$TMP"
  exit "$status"
}
trap cleanup EXIT INT TERM

wait_url() {
  local url=$1
  for _ in $(seq 1 120); do
    code=$(curl --silent --output /dev/null --write-out '%{http_code}' "$url" || true)
    if [[ $code != 000 ]]; then
      return
    fi
    sleep 1
  done
  echo "Timed out waiting for $url" >&2
  return 1
}

echo "Building local binaries..."
cargo build --manifest-path "$ROOT/Cargo.toml"
cargo build --manifest-path "$PUSH_ROOT/Cargo.toml"
mkdir -p "$ROOT/e2e/bin"
cp "$ROOT/target/debug/transfair" "$ROOT/e2e/bin/transfair"
cp "$PUSH_ROOT/target/debug/mainzelliste-onkostar-push" "$ROOT/e2e/bin/mainzelliste-onkostar-push"

echo "Starting Beam Connect..."
"$BEAM_ROOT/dev/start" stop >/dev/null 2>&1 || true
"$BEAM_ROOT/dev/start" ci >"$TMP/beam-start.log" 2>&1 &
beam_start_pid=$!
wait_url http://localhost:8081/v1/health
wait_url http://localhost:8082/v1/health
# dev/start also waits on its bundled HTTPS echo service, which is unrelated
# to this test and can hang on some Docker Desktop versions.
kill "$beam_start_pid" >/dev/null 2>&1 || true
wait "$beam_start_pid" >/dev/null 2>&1 || true

echo "Starting E2E services..."
"${COMPOSE[@]}" down -v --remove-orphans >/dev/null 2>&1 || true
if ! "${COMPOSE[@]}" up -d --build >"$TMP/compose-up.log" 2>&1; then
  cat "$TMP/compose-up.log" >&2
  exit 1
fi
wait_url http://localhost:18080
wait_url http://localhost:18081/process-pending
wait_url http://localhost:18082
wait_url http://localhost:18083/requests
wait_url http://localhost:18084/fhir/metadata
wait_url http://localhost:18085/fhir/metadata
wait_url http://localhost:18086/fhir/metadata
wait_url http://localhost:18087/fhir/metadata
wait_url http://localhost:18088/calls

echo "Initializing consent policies and template..."
if ! (cd "$TTP_ROOT" && sh assets/consent-policies.sh \
  http://localhost pleaseChangeMeToo 18080 >"$TMP/consent-init.log" 2>&1); then
  cat "$TMP/consent-init.log" >&2
  exit 1
fi

echo "Preloading matching patient at the remote site..."
curl --fail-with-body --silent --show-error \
  -X POST http://localhost:18082/fhir/Patient \
  -H 'mainzellisteApiKey: transFAIR-password' \
  -H 'Content-Type: application/fhir+json' \
  --data-binary '{
    "resourceType":"Patient",
    "identifier":[
      {"use":"secondary","system":"SESSION_ID"},
      {"use":"secondary","system":"PROJECT_1_ID"}
    ],
    "name":[{"use":"official","family":"Mustermann","given":["Max"]}],
    "birthDate":"1990-01-01"
  }' > "$TMP/remote-patient.json"

# ml_token TYPE [DATA]: prints a one-time token of the callback Mainzelliste.
ml_token() {
  local session
  session=$(curl --fail-with-body --silent --show-error -X POST http://localhost:18080/sessions \
    -H 'mainzellisteApiKey: pleaseChangeMeToo' | jq -er .sessionId)
  jq -nc --arg type "$1" --argjson data "${2:-null}" \
    '{type: $type, allowedUses: 1} + if $data == null then {} else {data: $data} end' \
    | curl --fail-with-body --silent --show-error -X POST "http://localhost:18080/sessions/$session/tokens" \
      -H 'mainzellisteApiKey: pleaseChangeMeToo' -H 'mainzellisteApiVersion: 3.4' \
      -H 'Content-Type: application/json' --data-binary @- | jq -er '.tokenId // .id'
}

# read_ids ID_TYPE ID: prints the callback Mainzelliste patient's IDs as an idType -> idString object.
read_ids() {
  curl --fail-with-body --silent --show-error "http://localhost:18080/patients?tokenId=$(ml_token readPatients \
    "$(jq -nc --arg type "$1" --arg id "$2" '{searchIds: [{idType: $type, idString: $id}], resultIds: ["DKFZ_BK_ID", "dhkiComId"]}')")" \
    | jq -c '.[0].ids | map({key: .idType, value: .idString}) | from_entries'
}

echo "Creating consented callback patient..."
KIS_ID=900001
jq -nc --arg kis "$KIS_ID" \
  '{fields: {vorname: "Max", nachname: "Mustermann", geburtstag: "01", geburtsmonat: "01", geburtsjahr: "1990"},
    ids: {KIS_ID: $kis}, sureness: true}' \
  | curl --fail-with-body --silent --show-error \
    -X POST "http://localhost:18080/patients?tokenId=$(ml_token addPatient '{"idTypes": ["pid", "KIS_ID"]}')" \
    -H 'mainzellisteApiVersion: 3.4' -H 'Content-Type: application/json' --data-binary @- >/dev/null
curl --fail-with-body --silent --show-error "http://localhost:18080/fhir/Questionnaire?status=active" \
  -H "Authorization: MainzellisteToken $(ml_token searchConsentTemplates)" \
  | jq -e '[.entry[].resource | select(any(.identifier[]?; .value == "dhki-consent-1-0-0"))][0]' > "$TMP/template.json"
# Mainzelliste validates the consent against the template's contained default consent.
jq --arg kis "$KIS_ID" --arg today "$(date +%F)" --arg policy "fhir/Questionnaire/$(jq -er .id "$TMP/template.json")" \
  '.contained[0]
   | .resourceType = "Consent" | .status = "active" | .dateTime = $today
   | .patient = {identifier: {system: "http://localhost:18080/id/KIS_ID", value: $kis}}
   | .policy = [{uri: $policy}]
   | (.. | objects | select(has("period")) | .period) = {start: "1970-01-01", end: "2999-12-31"}
   | del(.id, .meta.versionId, .meta.lastUpdated)' \
  "$TMP/template.json" > "$TMP/consent.json"
curl --fail-with-body --silent --show-error -X POST http://localhost:18080/fhir/Consent \
  -H "Authorization: MainzellisteToken $(ml_token addConsent)" \
  -H 'Content-Type: application/fhir+json' --data-binary @"$TMP/consent.json" >/dev/null
# Creating the callback ID makes Mainzelliste call onkostar-push, which forwards the patient to DHKI TransFAIR.
curl --fail-with-body --silent --show-error -X POST \
  "http://localhost:18080/ids/tobesynced?tokenId=$(ml_token createIds \
    "$(jq -nc --arg kis "$KIS_ID" '{searchIds: [{idType: "KIS_ID", idString: $kis}], idTypes: ["tobesynced"]}')")" \
  -H 'mainzellisteApiVersion: 3.4' > "$TMP/callback-id.json"
CALLBACK_ID=$(jq -er '.[] | select(.idType == "tobesynced") | .idString' "$TMP/callback-id.json")

for _ in $(seq 1 60); do
  curl --fail --silent http://localhost:18083/requests > "$TMP/requests.json"
  [[ $(jq 'length' "$TMP/requests.json") -gt 0 ]] && break
  sleep 1
done
REQUEST_ID=$(jq -er '.[0].id' "$TMP/requests.json")
PROJECT_ID=$(jq -er '.[0].project_id' "$TMP/requests.json")

BK_ID=$(read_ids tobesynced "$CALLBACK_ID" | jq -er .DKFZ_BK_ID)
[[ $(read_ids tobesynced "$CALLBACK_ID" | jq -r .dhkiComId) == "$PROJECT_ID" ]]

# request_linkage: sends the patient to DHKI TransFAIR again, as onkostar-push does.
# Prints the HTTP status and the ID of the linkage request.
request_linkage() {
  curl --fail-with-body --silent --show-error \
    "http://localhost:18080/fhir/Consent?patient:identifier=http://localhost:18080/id/KIS_ID%7C$KIS_ID" \
    -H "Authorization: MainzellisteToken $(ml_token searchConsents)" \
    | jq -c --arg bk "$BK_ID" --arg kis "$KIS_ID" \
      '{patient: {resourceType: "Patient", identifier: [{system: "DKFZ_BK_ID", value: $bk}, {system: "KIS_ID", value: $kis}],
                  name: [{use: "official", family: "Mustermann", given: ["Max"]}], birthDate: "1990-01-01"},
        consent: (.entry[0].resource | .patient = {identifier: {system: "DKFZ_BK_ID", value: $bk}})}' \
    | curl --silent --show-error -o "$TMP/linkage.json" -w '%{http_code}' \
      -X POST http://localhost:18081/request-linkage -H 'Content-Type: application/json' --data-binary @-
  echo " $(jq -r .id "$TMP/linkage.json")"
}

# deliver_response REQUEST_ID [SUBJECT_SYSTEM]: uploads the DIC's response bundle for the request to UMM.
deliver_response() {
  sed -e "s/<<data_request_id>>/$1/g" \
      -e "s/<<session_id>>/$(curl --fail --silent "http://localhost:18083/requests/$1" | jq -er .exchange_id)/g" \
      -e "s/\"SESSION_ID\"/\"${2:-SESSION_ID}\"/g" \
      "$ROOT/docs/examples/example_input_data.json" \
    | curl --fail-with-body --silent --show-error -X POST http://localhost:18085/fhir/Bundle \
      -H 'Content-Type: application/fhir+json' --data-binary @- >/dev/null
  curl --fail-with-body --silent --show-error -X POST http://localhost:18083/process-data >/dev/null
}

echo "Sending the pending patient again..."
[[ $(request_linkage) == "200 $REQUEST_ID" ]]
[[ $(curl --fail --silent http://localhost:18083/requests | jq length) == 1 ]]

deliver_response "$REQUEST_ID"
[[ $(curl --fail --silent "http://localhost:18083/requests/$REQUEST_ID" | jq -r .status) == Success ]]
curl --fail-with-body --silent --show-error -X POST http://localhost:18081/process-pending > "$TMP/process.json"
[[ $(jq -r .processed "$TMP/process.json") == 1 ]]

assert_identifier() {
  local base=$1 resource=$2 system=$3 value=$4
  curl --fail --silent "$base/fhir/$resource" | jq -e \
    --arg system "$system" --arg value "$value" \
    '.entry | length > 0 and all(.[]; .resource.subject.identifier.system == $system and .resource.subject.identifier.value == $value)' \
    >/dev/null
}

assert_identifier http://localhost:18086 Condition PROJECT_1_ID "$PROJECT_ID"
assert_identifier http://localhost:18086 Procedure PROJECT_1_ID "$PROJECT_ID"
assert_identifier http://localhost:18087 Condition DKFZ_BK_ID "$BK_ID"
assert_identifier http://localhost:18087 Procedure DKFZ_BK_ID "$BK_ID"
[[ $(curl --fail --silent http://localhost:18088/calls | jq -r .calls) == 1 ]]

echo "Sending the loaded patient again..."
read -r status SECOND_ID < <(request_linkage)
[[ $status == 201 && $SECOND_ID != "$REQUEST_ID" ]]
echo "Failing that request at UMM and sending the patient again..."
deliver_response "$SECOND_ID" WRONG_ID
[[ $(curl --fail --silent "http://localhost:18083/requests/$SECOND_ID" | jq -r .status) == Error ]]
read -r status THIRD_ID < <(request_linkage)
[[ $status == 201 && $THIRD_ID != "$SECOND_ID" ]]
[[ $(request_linkage) == "200 $THIRD_ID" ]]
[[ $(curl --fail --silent http://localhost:18083/requests | jq -c '[.[].project_id] | unique') == "[\"$PROJECT_ID\"]" ]]

echo "E2E passed: request $REQUEST_ID loaded into DHKI Blaze under BK ID $BK_ID."
