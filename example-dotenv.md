# Routine Connector

To run the Routine Connector application, a `.env` file is required. Otherwise, all the values mentioned in this file, 
have to be passed as cmd-line arguments.

## Key-Value pairs needed to be in the .env file

The following key-value pairs are needed in the `.env` file. Please provide appropriate values for the various 
`api key / credentials / pass / passphrase` related fields.

```
# Environment for Routine Connector
INSTITUTE_TTP_URL="http://localhost:8081"
INSTITUTE_TTP_API_KEY="please-change-me"
FHIR_REQUEST_URL="http://localhost:8085"
FHIR_REQUEST_CREDENTIALS="please-change-me"
FHIR_INPUT_URL="http://localhost:8086"
FHIR_INPUT_CREDENTIALS="please-change-me"
FHIR_OUTPUT_URL="http://localhost:8095"
FHIR_OUTPUT_CREDENTIALS="please-change-me"
DATABASE_URL="sqlite://data_requests.sql"
PROJECT_ID_SYSTEM="PROJECT_1_ID"
EXCHANGE_ID_SYSTEM="SESSION_ID"
RUST_LOG="warn,wip_routine_connector=debug"
no_proxy="localhost"
# Environment for Mainzelliste
ML_DB_PASS="please-change-me"
ML_ROUTINE_CONNECTOR_PASSPHRASE="please-change-me"
ML_DIZ_PASSPHRASE="please-change-me"
ML_LOG_LEVEL="debug"
```
