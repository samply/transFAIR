# TransFAIR

## Introduction

TransFAIR is a tool specialized in data integration for medical institutions. Instead of creating own ETL processes by hand, this tool facilitates certain data integration tasks like:

- **E**xtraction from source systems
- **T**ransformation into target schemata
- **L**oading into target system

TransFAIR is designed to

- minimize data integration effort for personnel at the sites, especially if connected to multiple networks
- be easily extensible with new dataset/mapping definitions
- thus accelerate and facilitate rollout of new features and dataset extensions
- provide more consistent data quality (because as long as the source data is okay, errors within TransFAIR's mappings can be fixed centrally)

The tool focuses on use-cases and IT systems encountered in network medical research in Germany, in particular:

- Tumor Documentation Systems based on the [ADT/GEKID dataset](https://www.gekid.de/adt-gekid-basisdatensatz), as found in German Comprehensive Cancer Centers and connected via the [German Cancer Consortium (DKTK)](https://dktk.dkfz.de/en/clinical-platform), e.g. CREDOS, GTDS, Onkostar
- The [CentraXX biobanking solution](https://www.kairos.de/produkte/centraxx-bio/) often found in German biobanks, which are networked under the umbrella of the [German Biobank Node](https://www.bbmri.de)
- [Bridgeheads](https://github.com/samply/bridgehead) as used in the above networks as well as the European [Biobanking and BioMolecular Research Infrastructure (BBMRI-ERIC)](https://bbmri-eric.eu)
- Data Integration Centers, as established by the [Medical Informatics Initiative](https://www.medizininformatik-initiative.de) and under the umbrella of the [Netzwerk Universitätsmedizin](https://www.netzwerk-universitaetsmedizin.de), based on the [MII Core Dataset in FHIR](https://simplifier.net/organization/koordinationsstellemii)

## Configuration

TransFAIR is configured using environment variables:

### Base Configuration

In the case of base configuration, transFAIR will check every 60 seconds for updates in the `SOURCE` and push new resources to `TARGET`.

| Variable          | Description                                                                                                                                                   | Default                    |
|-------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------|
| `SOURCE_URL`      | HTTP Address of the `SOURCE` datastore                                                                                                                        | http://localhost:8080/fhir |
| `SOURCE_USERNAME` | (Optional) Username for basic authentication                                                                                                                  |                            |
| `SOURCE_PASSWORD` | (Optional) Password for basic authentication                                                                                                                  |                            |
| `TARGET_URL`      | HTTP Address of the `TARGET` datastore                                                                                                                        | http://localhost:8090/fhir |
| `TARGET_USERNAME` | (Optional) Username for basic authentication                                                                                                                  |                            |
| `TARGET_PASSWORD` | (Optional) Password for basic authentication                                                                                                                  |                            |
| `DISABLESSL`      | If set to `true`, SSL verification will be disabled, allowing the tool to accept self-signed certificates. **(Use with caution in production environments!)** | `false`                    |
| `DATABASE_URL`    | The path for the sqlite database TransFAIR uses                                                                                                               | sqlite://data_requests.sql |

### Transformation

To enable transformation of resource between the `SOURCE` and `TARGET`, set the `PROFILE` to the desired profile (defaults to `mii2bbmri`):

- `fhircopy` - transfer FHIR resources Organization, Condition, Observation, Specimen, as well as Patients referenced in them unchanged from one FHIR server to another. This can be used to perform filtering and/or pseudonymisation across servers.
- `bbmri2mii` - load biosample information from a BBMRI-ERIC Bridgehead, transform into MII Core Dataset and load into a target (e.g. FHIR Store with MII Core Dataset).
- `mii2bbmri` - load the MII Core Dataset (usually from a FHIR server/façade providing the MII Core Dataset), transform in BBMRI-ERIC profiles and load into BBMRI-ERIC Bridgehead.
- `dicom2fhir` - load data from a DICOM source, transform into ImagingStudy resources and load into a target FHIR store.
- `amr` - load data from AMR (ECDC antimicrobial resistance) CSV files, transform into Patient and Observation resources and load into a target FHIR store.

The transformation is quite complex and therefore worth of it's own project: [https://github.com/samply/transFAIR-batch](https://github.com/samply/transFAIR-batch)

#### Profile: dicom2fhir
| Variable                  | Description                                                   | Default |
|---------------------------|---------------------------------------------------------------|---------|
| `IMGMETA_FROM_FHIR`       | Get DICOM metadata from the `SOURCE` datastore                | true    |
| `IMGMETA_DICOM_WEB_URL`   | Get DICOM metadata from the specified DICOM web URL           |         |
| `IMGMETA_DICOM_FILE_PATH` | Get DICOM metadata from the specified DICOM file or directory |         |

#### Profile: amr

| Variable                  | Description                                                   | Default |
|---------------------------|---------------------------------------------------------------|---------|
| `AMR_FILE_PATH`           | Get AMR data from the specified directory                     |         |

### Linkage with external Sources

TransFAIR is able to link already existing data in `TARGET` with incoming data from `SOURCE` by communicating with an existing `INSTITUTE_TTP`.
For this purpose, TransFAIR will open a rest api on the `/requests` endpoint (see: API).
On incoming requests (at least contains a FHIR patient resource) TransFAIR communicates with the `INSTITUTE_TTP` to generate two pseudonyms

- a `PROJECT_PSEUDONYM`, used for storage in `TARGET` 
- a `EXCHANGE_PSEUDONYM`, used for communication with the external source and stored in `REQUEST` 

and pushes the pseudonymized patient resource with only the `EXCHANGE_PSEUDONYM` to `REQUEST.`

The external source then needs to fetch new requests from `REQUEST`, resolve `EXCHANGE_PSEUDONYM` through `INSTITUTE_TTP` to it's own and push available data to `SOURCE`.

| Variable                | Description                                                              | Default |
|-------------------------|--------------------------------------------------------------------------|---------|
| `INSTITUTE_TTP_URL`     | The HTTP address of the sites TTP                                        | -       |
| `INSTITUTE_TTP_API_KEY` | The api key to use for authentication with the TTP                       | -       |
| `EXCHANGE_ID_SYSTEM`    | Id System in the ttp used to identify patient in the REQUEST_FHIR Server | -       |
| `PROJECT_ID_SYSTEM`     | Id System in the ttp used to identy data in the TARGET system            | -       |
| `REQUEST_URL`           | HTTP Address of the `REQUEST` datastore                                  | -       |
| `REQUEST_USERNAME`      | (Optional) Username for basic authentication                             | -       |
| `REQUEST_PASSWORD`      | (Optional) Password for basic authentication                             | -       |

## API

The API of TransFAIR is only needed in case of linkage with external sources. In the following examples, we asume that TransFAIR is running on `http://localhost:8080`.

### POST /requests

Create a new linkage request providing a FHIR [patient](https://www.hl7.org/fhir/patient.html) and optionally a FHIR [consent](https://hl7.org/fhir/consent.html) resource.

```
    POST http://localhost:8080/requests
    {
      "idat": <hl7-fhir-patient-resource>,
      "consent": (optional) <hl7-fhir-consent-resource>
    }
```

TransFAIR will request `EXCHANGE_ID` and `PROJECT_ID` from the `INSTITUTE_TTP` and on success return

```
    201 CREATED
    Location: http://localhost:8080/requests/{request-id}
```

### GET /requests/{request-id}

Get the status of a specified request.

```
    GET http://localhost:8080/requests/{request-id}
```

Currently provides the id of the request and one of 4 states:

- **created**, the request was send to `REQUEST`
- **data-loaded** loaded data from `SOURCE` to `TARGET`
- **update-availabe** new data is available in `SOURCE` that is not already loaded to `TARGET`
- **error** encountered an error while loading data from `SOURCE`

```
    200 OK
    {"id": "{request-id}", "status": "created|data-loaded|update-available|error"}
```

### GET /requests

Provides an overview of all requests processed by this instance.

```
    GET http://localhost:8080/requests
    200 OK
    [
      {"id": "{request-id}", "status": "created|data-loaded|update-available|error"}
    ]
```

## Setup a Development Environment

To setup a development environment you need to install [Rust](https://www.rust-lang.org/tools/install) and [Docker](https://docs.docker.com/engine/install/).
After setting up both, you can run the necessary external components with `docker compose up` (using [docker-compose.yml](./docker-compose.yml)) and 
then run and build the application with `cargo run` (which will use environment defined in [.cargo/config.toml](./.cargo/config.toml)).

You can run the integration tests against your running application using `cargo test`.

## Roadmap

:construction: This tool is still under intensive development. Features on the roadmap are:

- [ ] Pseudonymisation
- [ ] Double-check specimen type mappings 
- [ ] Smoking status mappings
- [ ] Body weight mapping
- [ ] BMI mappings
- [ ] Fasting status mappings
- [ ] Log unmappable codes
- [ ] Implement Beacon as a target format
- [ ] Additional code systems for diagnoses beyond ICD10GM
- [ ] Decide on using allowlist/denylist
- [ ] Decide on identifiers instead of IDs in references
- [ ] Incremental transfer, dealing with overwriting
- [ ] Integration tests
- [ ] Scheduler
- [ ] Support standards beyond [HL7 FHIR](https://hl7.org/fhir/), e.g. [OMOP](https://www.ohdsi.org/data-standardization/) and other well-known SQL, CSV and XML schemata.

To find out if TransFAIR can serve your needs today, we recommend you contact us to become a pilot site.

## License

This code is licensed under the Apache License 2.0. For details, please see [LICENSE](./LICENSE)

