#! /usr/bin/env bash
curl http://localhost:8080/requests \
-X POST \
-H 'Content-Type: application/json' \
--data-binary @- << EOF
{
  "patient": {
    "name": {
      "family": "Mustermann",
      "given": "Max",
      "prefix": []
    },
    "birth_date": "2000-01-01",
    "identifiers": ["PROJECT_1_ID", "SESSION_ID"]
  },
  "consent": {
    "resourceType": "Consent",
    "status": "active",
    "scope": {
        "coding": [
            {
                "system": "http://terminology.hl7.org/CodeSystem/consentscope",
                "code": "research"
            }
        ]
    },
    "category": [
        {
            "coding": [
                {
                    "system": "http://loinc.org",
                    "code": "57016-8"
                }
            ]
        }
    ],
    "dateTime": "2020-01-01",
    "organization": [
        {
            "display": "Some University Clinic"
        }
    ],
    "policy": [
        {
            "uri": "/Questionnaire/Mii-Broad-Consent"
        }
    ],
    "policyRule": {
        "extension": [
            {
                "url": "http://fhir.de/ConsentManagement/StructureDefinition/Xacml",
                "valueBase64Binary": "TUlJIEJDIEV4YW1wbGUgWEFDTUw="
            }
        ],
        "text": "siehe eingebettetes XACML"
    },
    "provision": {
        "type": "permit",
        "period": {
            "start": "2020-09-01",
            "end": "2050-08-31"
        },
        "provision": [
            {
                "type": "permit",
                "period": {
                    "start": "2020-09-01",
                    "end": "2025-08-31"
                },
                "code": [
                    {
                        "coding": [
                            {
                                "system": "urn:oid:2.16.840.1.113883.3.1937.777.24.5.3",
                                "code": "2.16.840.1.113883.3.1937.777.24.5.3.6",
                                "display": "MDAT_erheben"
                            }
                        ]
                    }
                ]
            },
            {
                "type": "permit",
                "period": {
                    "start": "2020-09-01",
                    "end": "2050-08-31"
                },
                "code": [
                    {
                        "coding": [
                            {
                                "system": "urn:oid:2.16.840.1.113883.3.1937.777.24.5.3",
                                "code": "2.16.840.1.113883.3.1937.777.24.5.3.7",
                                "display": "MDAT_speichern_verarbeiten"
                            }
                        ]
                    }
                ]
            },
            {
                "type": "permit",
                "period": {
                    "start": "2020-09-01",
                    "end": "2050-08-31"
                },
                "code": [
                    {
                        "coding": [
                            {
                                "system": "urn:oid:2.16.840.1.113883.3.1937.777.24.5.3",
                                "code": "2.16.840.1.113883.3.1937.777.24.5.3.8",
                                "display": "MDAT_wissenschaftlich_nutzen_EU_DSGVO_NIVEAU"
                            }
                        ]
                    }
                ]
            },
            {
                "type": "permit",
                "period": {
                    "start": "2020-09-01",
                    "end": "2025-08-31"
                },
                "code": [
                    {
                        "coding": [
                            {
                                "system": "urn:oid:2.16.840.1.113883.3.1937.777.24.5.3",
                                "code": "2.16.840.1.113883.3.1937.777.24.5.3.19",
                                "display": "BIOMAT_erheben"
                            }
                        ]
                    }
                ]
            },
            {
                "type": "permit",
                "period": {
                    "start": "2020-09-01",
                    "end": "2050-08-31"
                },
                "code": [
                    {
                        "coding": [
                            {
                                "system": "urn:oid:2.16.840.1.113883.3.1937.777.24.5.3",
                                "code": "2.16.840.1.113883.3.1937.777.24.5.3.20",
                                "display": "BIOMAT_lagern_verarbeiten"
                            }
                        ]
                    }
                ]
            },
            {
                "type": "permit",
                "period": {
                    "start": "2020-09-01",
                        "end": "2050-08-31"
                    },
                    "code": [
                        {
                            "coding": [
                                {
                                    "system": "urn:oid:2.16.840.1.113883.3.1937.777.24.5.3",
                                    "code": "2.16.840.1.113883.3.1937.777.24.5.3.22",
                                    "display": "BIOMAT_wissenschaftlich_nutzen_EU_DSGVO_NIVEAU"
                                }
                            ]
                        }
                    ]
                }
            ]
        }
    }
}
EOF