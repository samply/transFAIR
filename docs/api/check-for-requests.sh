#! /usr/bin/env bash
# call like this:
#   sh docs/api/check-for-requests.sh
#
# This script checks outputs new requests since 2025-05-15T13:00:00

curl -X GET "http://localhost:8085/fhir/Bundle?_lastUpdated=gt{2025-05-15T13:00:00}"
