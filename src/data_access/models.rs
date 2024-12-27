use fhir_sdk::r4b::resources::{Consent, Patient};
use serde::{Deserialize, Serialize};

#[derive(Default, Serialize, Deserialize, sqlx::Type)]
pub enum RequestStatus {
    Created = 1,
    _DataLoaded = 2,
    _UpdateAvailable = 3,
    #[default]
    Error = 4,
}

#[derive(Default, Serialize, Deserialize, sqlx::FromRow)]
pub struct DataRequest {
    pub id: String,
    pub project_id: String,
    pub status: RequestStatus,
}

impl DataRequest {
    pub fn new(id: String, project_id: String) -> Self {
        Self {
            id,
            project_id,
            status: RequestStatus::Created,
        }
    }
}

#[derive(Serialize, Deserialize, Clone)]
pub struct DataRequestPayload {
    pub patient: Patient,
    pub consent: Consent,
}
