use std::collections::HashMap;

use axum::{extract::Path, Json};
use chrono::NaiveDate;
use reqwest::StatusCode;
use serde::{Serialize, Deserialize};
use tracing::debug;
use uuid::Uuid;

use crate::mainzelliste::get_supported_ids;

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

#[derive(Serialize, Deserialize)]
struct Name {
    family: String,
    given: String,
    prefix: Vec<String>
}

type IdType = String;
type Identifier = String;

#[derive(Serialize, Deserialize)]
pub struct Patient {
    name: Name,
    birth_date: NaiveDate,
    identifiers: HashMap<IdType, Identifier>
}

// POST /requests; Creates a new Data Request
pub async fn create_data_request(Json(patient): Json<Patient>) -> Result<Json<DataRequest>, (StatusCode, &'static str)> {
    validate_data_request(patient).await?;
    let data_request = DataRequest {
      id: Uuid::new_v4(),
      status: RequestStatus::Error,
    };
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
    let are_ids_supported = patient.identifiers.keys()
            .all(|identifier| ttp_supported_ids.contains(identifier));
    if ! are_ids_supported {
        Err((StatusCode::BAD_REQUEST, "The TTP doesn't support one of the requested id types."))
    } else {
        Ok(())
    }
}
