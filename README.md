# TransFAIR

## Introduction

TransFAIR is a specialized tool for data integration for medical institutions. Instead of creating own ETL processes by hand, this tool facilitates certain data integration tasks like:

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

## Profiles

TransFAIR is shipped with so-called ETL profiles. Currently, these are:

- `fhircopy` - transfer FHIR resources Organization, Condition, Observation, Specimen, as well as Patients referenced in them unchanged from one FHIR server to another. This can be used to perform filtering and/or pseudonymisation across servers.
- `bbmri2mii` - load biosample information from a BBMRI-ERIC Bridgehead, transform into MII Core Dataset and load into a target (e.g. FHIR Store with MII Core Dataset).
- `mii2bbmri` - load the MII Core Dataset (usually from a FHIR server/façade providing the MII Core Dataset), transform in BBMRI-ERIC profiles and load into BBMRI-ERIC Bridgehead.
- `dicom2fhir` - load data from a DICOM source, transform into ImagingStudy resources and load into a target FHIR store.

## Configuration

TransFAIR is configured using environment variables:

| Variable                                  | Description                                                                | Default                    |
|-------------------------------------------|----------------------------------------------------------------------------|----------------------------|
| `FHIR_INPUT_URL`                          | HTTP Address of the `SOURCE` datastore                                     | http://localhost:8080/fhir |
| `FHIR_OUTPUT_URL`                         | HTTP Address of the `TARGET` datastore                                     | http://localhost:8090/fhir |                                                                                         |                                                                    |
| `PROFILE`                                 | Identifier of the TransFAIR profile to execute (see [Profiles](#profiles)) | mii2bbmri                  |                                                       |                              |
| `IMGMETA_FROM_FHIR`                       | Get DICOM metadata from the `SOURCE` datastore                             | true                       |                                                       |                              |
| `IMGMETA_DICOM_WEB_URL`                   | Get DICOM metadata from the specified DICOM web URL                        |                            |                                                       |                              |
| `IMGMETA_DICOM_FILE_PATH`                 | Get DICOM metadata from the specified DICOM file or directory              |                            |                                                       |                              |


## Setup a Development Environment

To setup a development environment, start two FHIR servers on localhost. Fill the first FHIR server with testdata and run the batch job in your IDE. Check the second FHIR server to see how your data was transferred.

```yml
fhir:
  source:
    url: http://localhost:8080/fhir # source store
  target:
    url: http://localhost:8090/fhir # target store
spring:
  profiles:
    active:
      mii2bbmri # profile to run
```

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

