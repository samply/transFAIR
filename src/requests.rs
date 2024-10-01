use axum::{extract::{Path, State}, Json};

use fhir_sdk::r4b::{codes::IdentifierUse, resources::{Consent, IdentifiableResource, Patient}, types::{Identifier, Reference}};
use reqwest::StatusCode;
use serde::{Serialize, Deserialize};
use sqlx::{Pool, Sqlite, types::Uuid};
use tracing::{trace, debug, error};

use crate::{config::Ttp, fhir::post_data_request, mainzelliste::{request_project_pseudonym, document_patient_consent, get_supported_ids}, CONFIG};

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

trait AddIdRequestExt: Sized {
    fn add_id_request(self, id: String) -> axum::response::Result<Self>;
}

impl AddIdRequestExt for Patient {
    fn add_id_request(mut self, id: String) -> axum::response::Result<Self> {
        let request = Identifier::builder()
            .r#use(IdentifierUse::Secondary)
            .system(id)
            .build()
            .map_err(|err| {
                // TODO: Ensure that this will be a fatal error, as otherwise the linkage will not be possible
                error!("Unable to add token request to data request. See error message: {}", err);
                (StatusCode::INTERNAL_SERVER_ERROR, "Unable to add token request to data request")
            })
            .unwrap();
        self.identifier.push(Some(request));
        Ok(self)
    }
}

trait PseudonymizableExt: Sized {
    fn pseudonymize(self) -> axum::response::Result<Self>;
}

impl PseudonymizableExt for Patient {
    fn pseudonymize(self) -> axum::response::Result<Self> {
        let id = self.id.clone().unwrap();
        let exchange_identifier_pos = self.identifier().iter().position(
            |x| x.clone().is_some_and(|y| y.system == Some(CONFIG.exchange_id_system.clone()))
        ).unwrap();
        let exchange_identifier  = self.identifier().get(exchange_identifier_pos).cloned();
        let pseudonymized_patient = Patient::builder()
            .id(id)
            .identifier(vec![
                exchange_identifier.unwrap()
            ])
            .build()
            .map_err(|err| 
                (StatusCode::INTERNAL_SERVER_ERROR, format!("Unable to create pseudonymized patient object {}", err)))?;
        Ok(pseudonymized_patient)
    }
}

// POST /requests; Creates a new Data Request
pub async fn create_data_request(
    State(database_pool): State<Pool<Sqlite>>,
    Json(payload): Json<DataRequestPayload>
) -> axum::response::Result<Json<DataRequest>> {
    let mut consent = payload.consent;
    let mut patient = payload.patient;
    if let Some(ttp) = &CONFIG.ttp {
        // TODO: Maybe change that check, so that we check if the configuration of routine connector is right and here only that the request is really allowed
        validate_data_request(&patient, &ttp).await?;
        patient = patient
          .add_id_request(CONFIG.exchange_id_system.clone())?
          .add_id_request(ttp.project_id_system.clone())?;
        patient = request_project_pseudonym(&mut patient, &ttp).await?;
        trace!("TTP Returned these patient with project pseudonym {:#?}", &patient);
        consent = document_patient_consent(consent, &patient, &ttp).await?;
        trace!("TTP returned this consent for Patient {:?}", consent);
    } else {
        // ohne) das vorhandensein des linkbaren Pseudonym überprüft werden (identifier existiert, eventuell mit Wert in Konfiguration abgleichen?)
        if !contains_exchange_identifier(&patient) {
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
        id: data_request_id,
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

fn link_patient_consent(consent: &Consent, patient: &Patient) -> Result<Consent, (StatusCode, &'static str)> {
    let mut linked_consent = consent.clone();
    let exchange_identifier= get_exchange_identifier(patient);
    let Some(exchange_identifier) = exchange_identifier else {
        return Err((StatusCode::INTERNAL_SERVER_ERROR, "Unable to generate exchange identifier"));
    };
    linked_consent.patient = Some(Reference::builder().identifier(exchange_identifier.clone()).build().expect("TODO: Handle this error"));
    Ok(linked_consent)
}

fn get_exchange_identifier(patient: &Patient) -> Option<&Identifier> {
    patient.identifier().into_iter().flatten().find(
        |x| x.system.as_ref() == Some(&CONFIG.exchange_id_system)
    )
}

fn contains_exchange_identifier(patient: &Patient) -> bool {
    patient.identifier().into_iter().flatten().any(
        |x| x.system.as_ref() == Some(&CONFIG.exchange_id_system)
    )
}
