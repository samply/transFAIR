use axum::{extract::Path, Json};
use serde::Serialize;
use tracing::debug;
use uuid::Uuid;

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

// POST /requests; Creates a new Data Request
pub async fn create_data_request() -> Json<DataRequest> {
    let data_request = DataRequest {
        id: Uuid::new_v4(),
        status: RequestStatus::Error,
    };
    Json(data_request)
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
