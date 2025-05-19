#!/usr/bin/env sh
# call from repository root:
#   sh docs/api/provide-data-for-request.sh
#
# this script provides exemplary for data request "ABCDEFGHIJKLMNOP" and
# the patient patient with SESSION_ID "12345678"

curl -k "http://localhost:8086/fhir/Bundle" \
-X POST \
-H "Content-Type: application/fhir+json" \
--data-binary @- <<EOF
{
    "identifier": {
        "use": "temp",
        "system": "DATAREQUEST_ID",
        "value": "ABCDEFGHIJKLMNOP"
    },
    "resourceType": "Bundle",
    "type": "transaction",
    "entry": [
        {
            "resource": {
                "clinicalStatus": {
                    "coding": [
                        {
                            "system": "http://terminology.hl7.org/CodeSystem/condition-clinical",
                            "code": "active"
                        }
                    ]
                },
                "meta": {
                    "versionId": "8",
                    "lastUpdated": "2024-03-22T13:52:42.493Z"
                },
                "onsetPeriod": {
                    "start": "2020-02-26T12:00:00+01:00",
                    "end": "2020-03-05T13:00:00+01:00"
                },
                "resourceType": "Condition",
                "recordedDate": "2020-02-26T12:00:00+01:00",
                "id": "DDTGN5W6O3JG7DSN",
                "code": {
                    "coding": [
                        {
                            "system": "http://fhir.de/CodeSystem/dimdi/icd-10-gm",
                            "version": "2020",
                            "code": "S50.0",
                            "display": "Prellung des Ellenbogens"
                        },
                        {
                            "system": "http://snomed.info/sct",
                            "code": "91613004",
                            "display": "Contusion of elbow (disorder)"
                        }
                    ],
                    "text": "Prellung des linken Ellenbogens"
                },
                "subject": {
                    "identifier": {
                        "system": "SESSION_ID",
                        "value": "12345678"
                    }
                }
            },
            "request": {
                "method": "POST",
                "url": "/Condition"
            }
        },
        {
            "resource": {
                "category": {
                    "coding": [
                        {
                            "system": "http://snomed.info/sct",
                            "code": "387713003",
                            "display": "Surgical procedure (procedure)"
                        }
                    ]
                },
                "meta": {
                    "versionId": "9",
                    "lastUpdated": "2024-03-22T13:56:10.253Z"
                },
                "resourceType": "Procedure",
                "status": "completed",
                "id": "DDTGOKMW4ML7HSCJ",
                "performedDateTime": "2020-04-23",
                "code": {
                    "coding": [
                        {
                            "system": "http://snomed.info/sct",
                            "code": "80146002",
                            "display": "Excision of appendix (procedure)"
                        },
                        {
                            "system": "http://fhir.de/CodeSystem/dimdi/ops",
                            "version": "2020",
                            "code": "5-470",
                            "display": "Appendektomie"
                        }
                    ]
                },
                "subject": {
                    "identifier": {
                        "system": "SESSION_ID",
                        "value": "12345678"
                    }
                }
            },
            "request": {
                "method": "POST",
                "url": "/Procedure"
            }
        }
    ]
}
EOF
