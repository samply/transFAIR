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

echo "Creating consented callback patient..."
(cd "$TTP_ROOT" && sh assets/create-patient-with-consent.sh \
  http://localhost:18080 pleaseChangeMeToo Max Mustermann 1990-01-01 \
  dhki-consent-1-0-0 tobesynced pid) > "$TMP/source-patient.json"
BK_ID=$(jq -er '.callbackId.idString' "$TMP/source-patient.json")

for _ in $(seq 1 60); do
  curl --fail --silent http://localhost:18083/requests > "$TMP/requests.json"
  [[ $(jq 'length' "$TMP/requests.json") -gt 0 ]] && break
  sleep 1
done
REQUEST_ID=$(jq -er '.[0].id' "$TMP/requests.json")
EXCHANGE_ID=$(jq -er '.[0].exchange_id' "$TMP/requests.json")

sed -e "s/<<data_request_id>>/$REQUEST_ID/g" \
    -e "s/<<session_id>>/$EXCHANGE_ID/g" \
    "$ROOT/docs/examples/example_input_data.json" > "$TMP/response-bundle.json"
curl --fail-with-body --silent --show-error \
  -X POST http://localhost:18085/fhir/Bundle \
  -H 'Content-Type: application/fhir+json' \
  --data-binary @"$TMP/response-bundle.json" >/dev/null

curl --fail-with-body --silent --show-error -X POST http://localhost:18083/process-data >/dev/null
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

PROJECT_ID=$(jq -er '.[0].project_id' "$TMP/requests.json")
assert_identifier http://localhost:18086 Condition PROJECT_1_ID "$PROJECT_ID"
assert_identifier http://localhost:18086 Procedure PROJECT_1_ID "$PROJECT_ID"
assert_identifier http://localhost:18087 Condition DKFZ_BK_ID "$BK_ID"
assert_identifier http://localhost:18087 Procedure DKFZ_BK_ID "$BK_ID"
[[ $(curl --fail --silent http://localhost:18088/calls | jq -r .calls) == 1 ]]

echo "E2E passed: request $REQUEST_ID loaded into DHKI Blaze under BK ID $BK_ID."
