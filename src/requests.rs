use axum::{extract::Path, Json};
use chrono::NaiveDate;
use fhir_sdk::r4b::resources::Consent;
use reqwest::StatusCode;
use serde::{Serialize, Deserialize};
use tracing::debug;
use uuid::Uuid;

use crate::mainzelliste::{create_project_pseudonym, document_patient_consent, get_supported_ids};

#[derive(Serialize)]
pub enum RequestStatus {
    Created,
    DataLoaded,
    UpdateAvailable,
    Error,
}

#[derive(Serialize)]
pub struct DataRequest {
    id: Uuid,
    status: RequestStatus,
}

#[derive(Serialize, Deserialize, Clone)]
pub struct Name {
    pub family: String,
    pub given: String,
    pub prefix: Vec<String>
}

type IdType = String;

#[derive(Serialize, Deserialize, Clone)]
pub struct Patient {
    pub name: Name,
    pub birth_date: NaiveDate,
    pub identifiers: Vec<IdType>
}

#[derive(Serialize, Deserialize, Clone)]
pub struct DataRequestPayload {
    pub patient: Patient,
    pub consent: Consent
}

// POST /requests; Creates a new Data Request
pub async fn create_data_request(Json(payload): Json<DataRequestPayload>) -> Result<Json<DataRequest>, (StatusCode, &'static str)> {
    validate_data_request(payload.patient.clone()).await?;
    let data_request = DataRequest {
      id: Uuid::new_v4(),
      status: RequestStatus::Created,
    };
    let identifiers = create_project_pseudonym(payload.patient.clone()).await?;
    debug!("TTP Returned these identifiers {:#?}", identifiers);
    let consent = document_patient_consent(payload.consent, identifiers).await?;
    debug!("TTP returned this consent for Patient {:?}", consent);
    Ok(Json(data_request))
}

// GET /requests; Lists all running Data Requests
pub async fn list_data_requests() -> Json<Vec<DataRequest>> {
    let mock_request = DataRequest {
        id: Uuid::new_v4(),
        status: RequestStatus::Error,
    };
    let mut data_requests = Vec::new();
    data_requests.push(mock_request);
    Json(data_requests)
}

// GET /requests/<request-id>; Gets the Request specified by id in Path
pub async fn get_data_request(Path(request_id): Path<Uuid>) -> Json<DataRequest> {
    debug!("get data request called for request {}", request_id);
    let data_request = DataRequest {
        id: request_id,
        status: RequestStatus::Error,
    };
    Json(data_request)
}

async fn validate_data_request(patient: Patient) -> Result<(), (StatusCode, &'static str)>  {
    let ttp_supported_ids = get_supported_ids().await?;
    let are_ids_supported = patient.identifiers
            .iter()
            .all(|identifier| ttp_supported_ids.contains(identifier));
    if ! are_ids_supported {
        Err((StatusCode::BAD_REQUEST, "The TTP doesn't support one of the requested id types."))
    } else {
        Ok(())
    }
}
