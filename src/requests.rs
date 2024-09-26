use axum::{extract::{Path, State}, Json};

use fhir_sdk::r4b::resources::{Consent, Patient};
use reqwest::StatusCode;
use serde::{Serialize, Deserialize};
use sqlx::{Pool, Sqlite, types::Uuid};
use tracing::{debug, error};

use crate::{fhir::post_data_request, mainzelliste::{create_project_pseudonym, document_patient_consent, get_supported_ids}, CONFIG, config::Ttp};

#[derive(Serialize, sqlx::Type)]
// #[repr(i8)]
pub enum RequestStatus {
    Created = 1,
    _DataLoaded = 2,
    _UpdateAvailable = 3,
    Error = 4,
}

#[derive(Serialize, sqlx::FromRow)]
pub struct DataRequest {
    id: String,
    status: RequestStatus,
}

#[derive(Serialize, Deserialize, Clone)]
pub struct Name {
    pub family: String,
    pub given: String,
    pub prefix: Vec<String>
}

#[derive(Serialize, Deserialize, Clone)]
pub struct DataRequestPayload {
    pub patient: Patient,
    pub consent: Consent
}

// POST /requests; Creates a new Data Request
pub async fn create_data_request(
    State(database_pool): State<Pool<Sqlite>>,
    Json(payload): Json<DataRequestPayload>
) -> Result<Json<DataRequest>, (StatusCode, &'static str)> {
    let mut consent = payload.consent;
    let mut patient = payload.patient;
    if let Some(ttp) = &CONFIG.ttp {
        // TODO: Maybe change that check, so that we check if the configuration of routine connector is right and here only that the request is really allowed
        validate_data_request(&patient, &ttp).await?;
        // mit) eine anfrage an die TTP übermittelt werden und dafür entsprechende Pseudonyme angefragt werden. Dafür muss validiert werden das die Anfrage des Projekts auch das richtige Projektpseudonym anfragt
        // TODO: Hier muss sichergestellt werden das das richtige Pseudonym erzeugt wird
        patient = create_project_pseudonym(&patient, &ttp).await?;
        debug!("TTP Returned these patient with project pseudonym {:#?}", &patient);
        consent = document_patient_consent(consent, &patient, &ttp).await?;
        debug!("TTP returned this consent for Patient {:?}", consent);
    } else {
        // ohne) das vorhandensein des linkbaren Pseudonym überprüft werden (identifier existiert, eventuell mit Wert in Konfiguration abgleichen?)
    }
    // und in beiden fällen anschließend die Anfrage beim Datenintegrationszentrum abgelegt werden
    let data_request_id = post_data_request(&CONFIG.consent_fhir_url, &CONFIG.consent_fhir_api_key, patient, consent).await?;

    let data_request = DataRequest {
        id: data_request_id,
        status: RequestStatus::Created,
    };

    let sqlite_query_result = sqlx::query!(
        "INSERT INTO data_requests (id, status) VALUES ($1, $2)",
        data_request.id, data_request.status
    ).execute(&database_pool).await.map_err(|e| {
        error!("Unable to persist data request to database. {}", e);
        (StatusCode::INTERNAL_SERVER_ERROR, "Unable to persist data request to database.")
    }).unwrap();

    let last_insert_rowid = sqlite_query_result.last_insert_rowid();
    debug!("Inserted data request in row {}", last_insert_rowid);

    Ok(Json(data_request))
}

// GET /requests; Lists all running Data Requests
pub async fn list_data_requests(
    State(database_pool): State<Pool<Sqlite>>
) -> Result<Json<Vec<DataRequest>>, (StatusCode, &'static str)> {
    let data_requests = sqlx::query_as!(
        DataRequest,
        r#"SELECT id as "id: uuid::Uuid", status as "status: _" FROM data_requests;"#,
    ).fetch_all(&database_pool).await.map_err(|e| {
       error!("Unable to fetch data requests from database: {}", e); 
       (StatusCode::INTERNAL_SERVER_ERROR, "Unable to fetch data requests from database!")
    }).unwrap();
    Ok(Json(data_requests))
}

// GET /requests/<request-id>; Gets the Request specified by id in Path
pub async fn get_data_request(
    State(database_pool): State<Pool<Sqlite>>,
    Path(request_id): Path<Uuid>
) -> Result<Json<DataRequest>, (StatusCode, &'static str)> {
    debug!("Information on data request {} requested.", request_id);
    let data_request = sqlx::query_as!(
        DataRequest,
        r#"SELECT id as "id: uuid::Uuid", status as "status: _" FROM data_requests WHERE id = $1;"#,
        request_id
    ).fetch_optional(&database_pool).await.map_err(|e| {
        error!("Unable to fetch data request {} from database: {}", request_id, e);
        (StatusCode::INTERNAL_SERVER_ERROR, format!("Unable to fetch data request with id {}", request_id))
    }).unwrap();
    match data_request {
        Some(data_request) => Ok(Json(data_request)),
        None => Err((StatusCode::NOT_FOUND, "Couldn't retrieve data request with id"))
    }
}

async fn validate_data_request(patient: &Patient, ttp: &Ttp) -> Result<(), (StatusCode, &'static str)>  {
    let ttp_supported_ids = get_supported_ids(&ttp).await?;
    let are_ids_supported = patient.identifier
            .iter()
            .flatten()
            .all(|identifier| {
                ttp_supported_ids
                .iter()
                .any(|supported| 
                    identifier.system.clone()
                    .ok_or(
                        (StatusCode::BAD_REQUEST, "Supplied identifier in request did not contain a system!")
                    )
                    .unwrap().eq(supported))
            });
    if ! are_ids_supported {
        Err((StatusCode::BAD_REQUEST, "The TTP doesn't support one of the requested id types."))
    } else {
        Ok(())
    }
}
