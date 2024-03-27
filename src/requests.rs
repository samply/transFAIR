use axum::{extract::Path, Json};
use reqwest::StatusCode;
use serde::Serialize;

#[derive(Serialize)]
pub enum RequestStatus {
    Created,
    DataLoaded,
    UpdateAvailable,
    Error
}

#[derive(Serialize)]
pub struct DataRequest {
    id: String, // TODO: Do this as UUID
    status: RequestStatus
}

// POST /requests; Creates a new Data Request
pub async fn create_data_request() -> Result<String, StatusCode> {
    let not_implemented_message = String::from("This should create a new data request");
    Ok(not_implemented_message)
}

// GET /requests; Lists all running Data Requests
pub async fn list_data_requests() -> Result<String, StatusCode> {
    let not_implemented_message = String::from("This should return a JSON string of data requests");
    Ok(not_implemented_message)
}

// GET /requests/<request-id>; Gets the Request specified by id in Path
pub async fn get_data_request(Path(request_id): Path<String>) -> Json<DataRequest>{
    let mock_request = DataRequest {
        id: request_id,
        status: RequestStatus::Error
    };
    Json(mock_request)
}
