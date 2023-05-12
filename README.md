# Transfyr

## Introduction

Transfyr is intended to be a ready-to-use tool (turnkey solution) for data integration for medical institutions. Instead of creating own ETL processes by hand, this tool facilitates certain data integration tasks like:

- **E**xtraction from source systems
- **T**ransformation into target schemata
- **L**oading into target systems

Right now 3 types of transfer are supported:
- bbmri2mii - transforming resources from BBMRI FHIR profiles to MII FHIR profiles
- mii2bbmri - transforming resources from MII FHIR profiles to BBMRI FHIR profiles
- copy - copying resources from one FHIR store to another without transforming them

## Setup Develop

To Setup a development environment start two FHIR servers on localhost. Fill the first FHIR server with testdata and run the batch job in your IDE
Check the second FHIR server to see how your data was transferred.

``` yml
fhir:
  input:
    url: http://localhost:8080/fhir # source store
  output:
    url: http://localhost:8090/fhir # target store
  exampleFhirConfig: test
data:
  outputFileDirectory: ./test
  writeBundlesToFile: false
spring:
  profiles:
    active:
      mii2bbmri # job to run
```


