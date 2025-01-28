use std::str::FromStr;

use axum::{extract::{Path, State}, Json};

use fhir_sdk::r4b::{resources::{Consent, Patient, ResourceType}, types::Reference};
use once_cell::sync::Lazy;
use reqwest::StatusCode;
use serde::{Serialize, Deserialize};
use sqlx::{Pool, Sqlite};
use tracing::{trace, debug, error};

use crate::{fhir::{FhirServer, PatientExt}, LinkageError, CONFIG};

static REQUEST_SERVER: Lazy<FhirServer> = Lazy::new(|| {
    FhirServer::new(CONFIG.fhir_request_url.clone(), CONFIG.fhir_request_credentials.clone())
});

#[derive(Serialize, Deserialize, sqlx::Type)]
pub enum RequestStatus {
    Created = 1,
    Success = 2,
    Error = 3,
}

#[derive(Serialize, Deserialize, sqlx::FromRow)]
pub struct DataRequest {
    pub id: String,
    pub status: RequestStatus,
    pub message: Option<String>,
    pub exchange_id: String,
    pub project_id: Option<String>,
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

    let mut project_identifier = None;

    if let Some(ttp) = &CONFIG.ttp {
        patient = patient
          .add_id_request(CONFIG.exchange_id_system.clone())?
          .add_id_request(ttp.project_id_system.clone())?;
        // pseudonymize the patient
        patient = ttp.request_project_pseudonym(&mut patient).await?;
        // now, the patient should have project1id data (which can be stored in the DB)
        trace!("TTP Returned these patient with project pseudonym {:#?}", &patient);
        consent = ttp.document_patient_consent(consent, &patient).await?;
        trace!("TTP returned this consent for Patient {:?}", consent);

        // TODO: Safe way for unwrap
        project_identifier = patient.get_identifier(&ttp.project_id_system).cloned().unwrap().value.clone();
    }

    // ensure that we have at least one identifier with which we can link
    let Some(exchange_identifier) = patient.get_identifier(&CONFIG.exchange_id_system).cloned() else {
        return Err(
            (StatusCode::BAD_REQUEST, format!("Couldn't identify a valid identifier with system {}!", &CONFIG.exchange_id_system)).into()
        );
    };

    let Some(ref exchange_identifier) = exchange_identifier.value else {
        return Err(
            (StatusCode::BAD_REQUEST, format!("No valid value for identifier {}", &CONFIG.exchange_id_system)).into()
        )
    };

    patient = patient.pseudonymize()?;
    consent = link_patient_consent(&consent, &patient)?;
    // und in beiden fällen anschließend die Anfrage beim Datenintegrationszentrum abgelegt werden 
    let data_request_id = REQUEST_SERVER.post_data_request(DataRequestPayload {
        patient,
        consent
    }).await?;

    let data_request = DataRequest {
        id: data_request_id,
        status: RequestStatus::Created,
        message: Some(String::from_str("Data Request created!").unwrap()),
        exchange_id: exchange_identifier.to_string(),
        project_id: project_identifier
    };

    // storage for associated project id
    let sqlite_query_result = sqlx::query!(
        "INSERT INTO data_requests (id, status, message, exchange_id, project_id) VALUES ($1, $2, $3, $4, $5)",
        data_request.id, data_request.status, data_request.message, data_request.exchange_id, data_request.project_id
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
        r#"SELECT id, status as "status: _", message, exchange_id, project_id FROM data_requests;"#,
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
        r#"SELECT id, status as "status: _", message, exchange_id, project_id FROM data_requests WHERE id = $1;"#,
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
    let exchange_identifier= patient.get_identifier(&CONFIG.exchange_id_system);
    let Some(exchange_identifier) = exchange_identifier else {
        return Err((StatusCode::INTERNAL_SERVER_ERROR, "Unable to generate exchange identifier"));
    };
    linked_consent.patient = Some(Reference::builder().identifier(exchange_identifier.clone()).build().expect("TODO: Handle this error"));
    Ok(linked_consent)
}

pub async fn update_data_request(bundle_identifier: &str, linkage_results: Option<Vec<Result<ResourceType, LinkageError>>>, database_pool: &Pool<Sqlite>) -> sqlx::Result<()> {
    let Some(linkage_results) = linkage_results else {
        let message_success_without_linkage = format!("Transferred data from input to output FHIR server without linkage.");
        let _ = sqlx::query!(
            "UPDATE data_requests SET status = $1, message = $2 WHERE id=$3",
            RequestStatus::Success, message_success_without_linkage, bundle_identifier
        ).execute(database_pool).await?;
        return Ok(());
    };
    let result_summary = linkage_results.iter().map(|res| match res {
        Ok(rt) => format!("{rt}()"),
        Err(error) => format!("{error}"),
    }).collect::<Vec<String>>().join(",");
    debug!("{}", result_summary);

    let result_status = match linkage_results.iter().any(Result::is_err) {
        true => RequestStatus::Error,
        false => RequestStatus::Success,
    };

    let _ = sqlx::query!(
        "UPDATE data_requests SET status = $1, message = $2 WHERE id=$3",
        result_status, result_summary, bundle_identifier
    ).execute(database_pool).await?;
    Ok(())
}
