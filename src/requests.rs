use axum::{extract::{Path, State}, Json};

use fhir_sdk::r4b::{resources::{Consent, Patient}, types::Reference};
use reqwest::StatusCode;
use serde::{Serialize, Deserialize};
use sqlx::{Pool, Sqlite};
use tracing::{trace, debug, error};

use crate::{fhir::{post_data_request, LinkableExt, PseudonymizableExt}, CONFIG};

#[derive(Serialize, Deserialize, sqlx::Type)]
pub enum RequestStatus {
    Created = 1,
    _DataLoaded = 2,
    _UpdateAvailable = 3,
    Error = 4,
}

#[derive(Serialize, Deserialize, sqlx::FromRow)]
pub struct DataRequest {
    pub id: String,
    pub status: RequestStatus,
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
) -> axum::response::Result<(StatusCode, Json<DataRequest>)> {
    let mut consent = payload.consent;
    let mut patient = payload.patient;
    if let Some(ttp) = &CONFIG.ttp {
        patient = patient
          .add_id_request(CONFIG.exchange_id_system.clone())?
          .add_id_request(ttp.project_id_system.clone())?;
        patient = ttp.request_project_pseudonym(&mut patient).await?;
        trace!("TTP Returned these patient with project pseudonym {:#?}", &patient);
        consent = ttp.document_patient_consent(consent, &patient).await?;
        trace!("TTP returned this consent for Patient {:?}", consent);
    } else {
        // ohne) das vorhandensein des linkbaren Pseudonym überprüft werden (identifier existiert, eventuell mit Wert in Konfiguration abgleichen?)
        if !patient.contains_exchange_identifier() {
            return Err(
                (StatusCode::BAD_REQUEST, format!("Couldn't identify a valid identifier with system {}!", &CONFIG.exchange_id_system)).into()
            );
        }
    }
    patient = patient.pseudonymize()?;
    consent = link_patient_consent(&consent, &patient)?;
    // und in beiden fällen anschließend die Anfrage beim Datenintegrationszentrum abgelegt werden
    let data_request_id = post_data_request(&CONFIG.fhir_request_url, &CONFIG.fhir_request_credentials, patient, consent).await?;

    let data_request = DataRequest {
        id: dbg!(data_request_id),
        status: RequestStatus::Created,
    };

    let sqlite_query_result = sqlx::query!(
        "INSERT INTO data_requests (id, status) VALUES ($1, $2)",
        data_request.id, data_request.status
    ).execute(&database_pool).await.map_err(|e| {
        error!("Unable to persist data request to database. {}", e);
        (StatusCode::INTERNAL_SERVER_ERROR, "Unable to persist data request to database.")
    })?;

    let last_insert_rowid = sqlite_query_result.last_insert_rowid();
    debug!("Inserted data request in row {}", last_insert_rowid);

    Ok((StatusCode::CREATED, Json(data_request)))
}

// GET /requests; Lists all running Data Requests
pub async fn list_data_requests(
    State(database_pool): State<Pool<Sqlite>>
) -> Result<Json<Vec<DataRequest>>, (StatusCode, &'static str)> {
    let data_requests = sqlx::query_as!(
        DataRequest,
        r#"SELECT id, status as "status: _" FROM data_requests;"#,
    ).fetch_all(&database_pool).await.map_err(|e| {
       error!("Unable to fetch data requests from database: {}", e); 
       (StatusCode::INTERNAL_SERVER_ERROR, "Unable to fetch data requests from database!")
    }).unwrap();
    Ok(Json(data_requests))
}

// GET /requests/<request-id>; Gets the Request specified by id in Path
pub async fn get_data_request(
    State(database_pool): State<Pool<Sqlite>>,
    Path(request_id): Path<String>
) -> Result<Json<DataRequest>, (StatusCode, &'static str)> {
    debug!("Information on data request {} requested.", request_id);
    let data_request = sqlx::query_as!(
        DataRequest,
        r#"SELECT id, status as "status: _" FROM data_requests WHERE id = $1;"#,
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

fn link_patient_consent(consent: &Consent, patient: &Patient) -> Result<Consent, (StatusCode, &'static str)> {
    let mut linked_consent = consent.clone();
    let exchange_identifier= patient.get_exchange_identifier();
    let Some(exchange_identifier) = exchange_identifier else {
        return Err((StatusCode::INTERNAL_SERVER_ERROR, "Unable to generate exchange identifier"));
    };
    linked_consent.patient = Some(Reference::builder().identifier(exchange_identifier.clone()).build().expect("TODO: Handle this error"));
    Ok(linked_consent)
}
