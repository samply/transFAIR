use axum::{
    extract::{Path, State},
    Json,
};

use fhir_sdk::r4b::{
    resources::{Consent, Patient},
    types::Reference,
};
use once_cell::sync::Lazy;
use reqwest::StatusCode;
use sqlx::{Pool, Sqlite};
use tracing::{debug, error, trace};

use crate::{
    data_access::{
        data_requests::{exists, get_all, get_by_id, insert},
        models::{DataRequest, DataRequestPayload},
    },
    fhir::{FhirServer, PatientExt},
    CONFIG,
};

static REQUEST_SERVER: Lazy<FhirServer> = Lazy::new(|| {
    FhirServer::new(
        CONFIG.fhir_request_url.clone(),
        CONFIG.fhir_request_credentials.clone(),
    )
});

// POST /requests; Creates a new Data Request
pub async fn create_data_request(
    State(database_pool): State<Pool<Sqlite>>,
    Json(payload): Json<DataRequestPayload>,
) -> axum::response::Result<(StatusCode, Json<DataRequest>)> {
    let mut consent = payload.consent;
    let mut patient = payload.patient;
    let mut project_id = "DEFAULT_PROJECT_ID";

    if let Some(ttp) = &CONFIG.ttp {
        project_id = &ttp.project_id_system;
        patient = patient
            .add_id_request(CONFIG.exchange_id_system.clone())?
            .add_id_request(project_id.into())?;
        // pseudonymize the patient
        patient = ttp.request_project_pseudonym(&mut patient).await?;
        // now, the patient should have project1id data (which can be stored in the DB)
        trace!(
            "TTP Returned these patient with project pseudonym {:#?}",
            &patient
        );
        consent = ttp.document_patient_consent(consent, &patient).await?;
        trace!("TTP returned this consent for Patient {:?}", consent);
    } else {
        // ohne) das vorhandensein des linkbaren Pseudonym überprüft werden (identifier existiert, eventuell mit Wert in Konfiguration abgleichen?)
        if !patient.contains_exchange_identifier() {
            return Err((
                StatusCode::BAD_REQUEST,
                format!(
                    "Couldn't identify a valid identifier with system {}!",
                    &CONFIG.exchange_id_system
                ),
            )
                .into());
        }
    }

    // TODO: check if this id, project_id combination exists in the DB (and then only post the request)
    let Some(patient_id) = patient.id.clone() else {
        let err_str = format!("Couldn't find a patient without a valid id!");
        let err_tuple = (StatusCode::BAD_REQUEST,err_str);
        return Err(err_tuple.into());
    };

    debug!("patient id: {patient_id}");
    if exists(&database_pool, &patient_id, project_id).await {
        let err_str = format!("A request for a patient {} in the project {} has already been generated!", patient_id, project_id);
        let err_tuple = (StatusCode::BAD_REQUEST,err_str);
        return Err(err_tuple.into());
    }

    patient = patient.pseudonymize()?;
    consent = link_patient_consent(&consent, &patient)?;
    // und in beiden fällen anschließend die Anfrage beim Datenintegrationszentrum abgelegt werden
    let data_request_id = REQUEST_SERVER
        .post_data_request(DataRequestPayload { patient, consent })
        .await?;

    let data_request = DataRequest::new(data_request_id, patient_id, project_id.into());
    // storage for associated project id
    let last_insert_rowid = insert(&database_pool, &data_request)
    .await
    .map_err(|e| {
        error!("Unable to persist data request to database. {}", e);
        (
            StatusCode::INTERNAL_SERVER_ERROR,
            "Unable to persist data request to database.",
        )
    })?;

    debug!("Inserted data request in row {}", last_insert_rowid);
    Ok((StatusCode::CREATED, Json(data_request)))
}

// GET /requests; Lists all running Data Requests
pub async fn list_data_requests(
    State(database_pool): State<Pool<Sqlite>>,
) -> Result<Json<Vec<DataRequest>>, (StatusCode, &'static str)> {
    get_all(&database_pool)
        .await
        .map_err(|e| {
            error!("Unable to fetch data requests from database: {}", e);
            (
                StatusCode::INTERNAL_SERVER_ERROR,
                "Unable to fetch data requests from database!",
            )
        })
        .map(Json)
}

// GET /requests/<request-id>; Gets the Request specified by id in Path
pub async fn get_data_request(
    State(database_pool): State<Pool<Sqlite>>,
    Path(request_id): Path<String>,
) -> Result<Json<DataRequest>, (StatusCode, String)> {
    debug!("Information on data request {} requested.", request_id);
    let data_request = get_by_id(&database_pool, &request_id)
        .await
        .map_err(|e| {
            error!(
                "Unable to fetch data request {} from database: {}",
                request_id, e
            );
            (
                StatusCode::INTERNAL_SERVER_ERROR,
                format!("Unable to fetch data request with id {}", request_id),
            )
        })?
        .map(Json);

    data_request.ok_or((
        StatusCode::NOT_FOUND,
        "Couldn't retrieve data request with id".to_string(),
    ))
}

fn link_patient_consent(
    consent: &Consent,
    patient: &Patient,
) -> Result<Consent, (StatusCode, &'static str)> {
    let mut linked_consent = consent.clone();
    let exchange_identifier = patient.get_exchange_identifier();
    let Some(exchange_identifier) = exchange_identifier else {
        return Err((
            StatusCode::INTERNAL_SERVER_ERROR,
            "Unable to generate exchange identifier",
        ));
    };
    linked_consent.patient = Some(
        Reference::builder()
            .identifier(exchange_identifier.clone())
            .build()
            .expect("TODO: Handle this error"),
    );
    Ok(linked_consent)
}
