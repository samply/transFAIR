use std::{process::ExitCode, time::Duration};

use axum::{routing::{get, post}, Router};
use chrono::{DateTime, Utc};
use config::Config;
use fhir::FhirServer;
use fhir_sdk::r4b::resources::{Bundle, Resource, ResourceType};
use once_cell::sync::Lazy;
use requests::update_data_request;
use sqlx::{Pool, Sqlite, SqlitePool};
use futures_util::future::TryJoinAll;
use tracing::{debug, error, info, trace, warn, Level};
use tracing_subscriber::{EnvFilter, util::SubscriberInitExt};
use ttp::Ttp;

use crate::requests::{create_data_request, list_data_requests, get_data_request};

mod banner;
mod config;
mod fhir;
mod requests;
mod ttp;

static CONFIG: Lazy<Config> = Lazy::new(Config::parse);
static SERVER_ADDRESS: &str = "0.0.0.0:8080";

#[tokio::main]
async fn main() -> ExitCode {
    tracing_subscriber::FmtSubscriber::builder()
        .with_max_level(Level::DEBUG)
        .with_env_filter(EnvFilter::from_default_env())
        .finish()
        .init();
    banner::print_banner();
    trace!("{:#?}", Lazy::force(&CONFIG));

    let database_pool = SqlitePool::connect(CONFIG.database_url.as_str())
        .await.map_err(|e| {
            error!("Unable to connect to database file {}. Error is: {}", CONFIG.database_url.as_str(), e);
            return
        }).unwrap();
    
    let _ = sqlx::migrate!().run(&database_pool).await;

    if let Some(ttp) = &CONFIG.ttp {
        const RETRY_COUNT: i32 = 30;
        let mut failures = 0;
        while !(ttp.check_availability().await) {
            failures += 1;
            if failures >= RETRY_COUNT {
                error!(
                    "Encountered too many errors -- exiting after {} attempts.",
                    RETRY_COUNT
                );
                return ExitCode::from(22);
            }
            tokio::time::sleep(Duration::from_secs(2)).await;
            warn!(
                "Retrying connection (attempt {}/{})",
                failures, RETRY_COUNT
            );
        }
        info!("Connected to ttp {}", ttp.url);
        // verify that both, the exchange id system and project id system are configured in the ttp
        for idtype in [&CONFIG.exchange_id_system, &ttp.project_id_system] {
            if !(ttp.check_idtype_available(&idtype).await) {
                error!("Configured exchange id system is not available in TTP: expected {}", &idtype);
                return ExitCode::from(1)
            }
        }
    }

    let database_pool_for_axum = database_pool.clone();

    tokio::spawn(async move {
        const RETRY_PERIOD: Duration = Duration::from_secs(60);
        let input_fhir_server =  FhirServer::new(
            CONFIG.fhir_input_url.clone(),
            CONFIG.fhir_input_credentials.clone()
        );
        let output_fhir_server =  FhirServer::new (
            CONFIG.fhir_output_url.clone(),
            CONFIG.fhir_output_credentials.clone()
        );
        loop {
            // TODO: Persist the updated data in the database
            match fetch_data(&input_fhir_server, &output_fhir_server, &database_pool).await {
                Ok(status) => info!("{}", status),
                Err(error) => warn!("Failed to fetch project data: {error:#}. Will try again in {}s", RETRY_PERIOD.as_secs())
            }
            tokio::time::sleep(RETRY_PERIOD).await;
        }
    });

    // request api endpoint
    let request_routes = Router::new()
        .route("/", post(create_data_request))
        .route("/", get(list_data_requests))
        .route("/{request_id}", get(get_data_request))
        .with_state(database_pool_for_axum);

    let app = Router::new()
        .nest("/requests", request_routes);

    let listener = tokio::net::TcpListener::bind(SERVER_ADDRESS).await.unwrap();
    axum::serve(listener, app)
        .await
        .unwrap();

    ExitCode::from(0)
}


// Pull data from input_fhir_server and push it to output_fhir_server
async fn fetch_data(input_fhir_server: &FhirServer, output_fhir_server: &FhirServer, database_pool: &Pool<Sqlite>) -> anyhow::Result<String> {
    let fetch_start_date = extract_execution_time(database_pool).await;
    let mut new_data = input_fhir_server.pull_new_data(
        fetch_start_date.naive_local().into()
    ).await?;
    let fetch_finish_date = chrono::prelude::Utc::now();
    if new_data.entry.is_empty() {
        debug!("Received empty bundle from mdat server ({}). No update necessary", CONFIG.fhir_input_url);
    } else {
        for entry in new_data.entry.iter_mut().flatten() {
            let Some(resource) = &mut entry.resource else {
                error!("Received invalid bundle for data request");
                continue;
            };

            let entry_bundle = match resource {
                Resource::Bundle(bundle) => bundle,
                _ => continue,
            };

            let Some(bundle_id) = entry_bundle.identifier.as_ref().cloned() else {
                error!("Received bundle without identifier. No link to data request is possible.");
                continue;
            };

            let Some(ref bundle_id_system) = bundle_id.system else {
                error!("Bundle identifier contains no system.");
                continue;
            };

            if bundle_id_system != "DATAREQUEST_ID" {
                error!("Bundle identifier has invalid system. Please provide an identifier with system \"DATAREQUEST_ID\"");
                continue;
            };

            let Some(ref bundle_id_value) = bundle_id.value else {
                error!("Bundle identifier has no value. Link to data request not possible");
                continue;
            };

            let mut linkage_results = None;
            if let Some(ttp) = &CONFIG.ttp {
                linkage_results = Some(replace_exchange_identifiers(bundle_id_value, entry_bundle, ttp, &database_pool).await?);
            };

            // TODO: integrate transformation using transfair-batch here

            match output_fhir_server.post_data(&entry_bundle).await{
                Ok(response) => info!("Received a response: {}", response.text().await.as_deref().unwrap_or("<invalid text>")),
                Err(error) => error!("Received the following error: {error:#}"),
            };

            update_data_request(&entry_bundle.id.as_ref().unwrap(), linkage_results, &database_pool).await.unwrap();

        }

    }
    let finish_as_timestamp = fetch_finish_date.timestamp_millis();
    let _ = sqlx::query!(
        "UPDATE last_request SET execution_time = $1 WHERE id = 1", finish_as_timestamp
    ).fetch_optional(database_pool).await;
    Ok(format!("Last fetch for new data executed at {:?}", fetch_finish_date))
}

async fn extract_execution_time(database_pool: &Pool<Sqlite>) -> DateTime<Utc> {
    let last_request = match sqlx::query!(
        "SELECT execution_time FROM last_request"
    ).fetch_optional(database_pool).await {
       Ok(last_request) => last_request,
       Err(_) => None,
    };

    match last_request {
        Some(last_request) => chrono::DateTime::from_timestamp_millis(
            last_request.execution_time
        ).expect("Unable to parse value in last_request.execution_time to data time. Expecting milliseconds since last unix epoch."),
        None => chrono::prelude::Utc::now(),
    }
}

    #[derive(Debug, thiserror::Error)]
enum LinkageError {
    #[error("entry in response did not contain a resource.")]
    EntryWithoutResource,
    #[error("The resource provided is of unknown type.")]
    UnknownResource(ResourceType),
    #[error("The provided resource didn't contain a reference.")]
    NoReference(ResourceType),
    #[error("Can't link resource due to missing identifier")]
    MissingIdentifier(ResourceType),
    #[error("DataRequest didn't have identifier value for project id stored")]
    MissingIdentifierValue(ResourceType),
    #[error("Identifier for linkage didn't contain a system")]
    IdentifierWithoutSystem(ResourceType),
    #[error("Identifier for linkage was not of configured type")]
    WrongIdentifierType(ResourceType),
    #[error("{0}: Unable to link identifier to any data request")]
    IdentifierNotLinkable(ResourceType)
}

async fn replace_exchange_identifiers(data_request_identifier: &str, new_data: &mut Bundle, ttp: &Ttp, database_connection: &Pool<Sqlite>) -> sqlx::Result<Vec<Result<ResourceType, LinkageError>>> {
    new_data.entry.iter_mut().flatten().map(|entry| {
        let Some(resource) = &mut entry.resource else {
            return Err(LinkageError::EntryWithoutResource)
        };

        let rt = resource.resource_type();
        let reference = match resource {
            Resource::Consent(consent) => match consent.patient.as_mut() {
                Some(patient) => patient,
                None => return Err(LinkageError::NoReference(rt))
            },
            Resource::Condition(condition) => &mut condition.subject,
            Resource::Procedure(procedure) => &mut procedure.subject,
            _ => return Err(LinkageError::UnknownResource(rt))
        };

        let Some(identifier) = &mut reference.identifier else {
            return Err(LinkageError::MissingIdentifier(rt))
        };

        let Some(system) = &mut identifier.system else {
            return Err(LinkageError::IdentifierWithoutSystem(rt))
        };

        if system != &CONFIG.exchange_id_system {
            Err(LinkageError::WrongIdentifierType(rt))
        } else {
            Ok((identifier, rt))
        }
    }).map(|res| async {
        let (ident, rt) = match res {
            Ok(items) => items,
            Err(e) => return Ok(Err(e)),
        };
        ident.system = Some(ttp.project_id_system.clone());
        let result = sqlx::query!(
            "SELECT project_id FROM data_requests WHERE id = $1",
            data_request_identifier
        ).fetch_optional(database_connection).await?;
        if let Some(patient_identifier) = result {
            let Some(project_id) = patient_identifier.project_id else {
                return Ok(Err(LinkageError::MissingIdentifierValue(rt)));
            };
            ident.value = Some(project_id);
            Ok(Ok(rt))
        } else {
            Ok(Err(LinkageError::IdentifierNotLinkable(rt)))
        }
    }).collect::<TryJoinAll<_>>().await
}

#[cfg(test)]
mod tests {
    use core::time;

    use fhir_sdk::r4b::resources::{Bundle, Resource};
    use pretty_assertions::assert_eq;
    use reqwest::StatusCode;

    use crate::requests::DataRequest;

    async fn post_data_request() -> DataRequest {
        let bytes = include_bytes!("../docs/examples/data_request.json");
        let json = &serde_json::from_slice::<serde_json::Value>(bytes).unwrap();

        let response = reqwest::Client::new()
            .post(format!("http://localhost:8080/requests"))
            .json(json)
            .send()
            .await
            .expect("POST endpoint (/requests) should give a valid response");
        assert_eq!(response.status(), StatusCode::CREATED);
        response.json().await.unwrap()
    }

    #[tokio::test]
    async fn get_data_request() {
        let data_request = post_data_request().await;
        let url = format!("http://localhost:8080/requests/{}", data_request.id);

        let response = reqwest::Client::new()
            .get(url)
            .send()
            .await
            .expect("GET endpoint (/requests/id) should give a valid response");
        assert_eq!(response.status(), StatusCode::OK);
    }

    #[tokio::test]
    async fn import_example_data() {
        // create a new data request
        let data_request = post_data_request().await;

        // prepare proper response by external site
        let bytes = include_bytes!("../docs/examples/example_input_data.json");
        let json_string = String::from_utf8_lossy(bytes);
        let json = serde_json::from_str::<Bundle>(
            &json_string
                .replace("<<data_request_id>>", &format!("{}", data_request.id.clone()))
                .replace("<<session_id>>", &format!("{}", data_request.exchange_id))
        ).unwrap();

        // deliver data from external site
        reqwest::Client::new()
            .post("http://localhost:8086/fhir/Bundle")
            .json(&json)
            .header("Content-Type", "application/fhir+json")
            .send()
            .await
            .expect("POST to (/fhir/Bundle) should given a valid response");

        // sleep for 70 seconds to ensure a fetch job runs during the test
        tokio::time::sleep(time::Duration::from_secs(70)).await;

        let procedure_response = reqwest::Client::new()
            .get("http://localhost:8095/fhir/Procedure")
            .send()
            .await.unwrap()
            .json::<Bundle>()
            .await.unwrap();

        assert!(!procedure_response.entry.is_empty());
        for entry in procedure_response.entry.iter() {
            assert_ne!(entry.is_none(), true);
            let ref procedure = match entry.clone().unwrap().resource.unwrap() {
                Resource::Procedure(procedure) => procedure,
                _ => continue,
            };
            // ensure identifier was changed to the project id
            let identifier = procedure.subject.identifier.clone().unwrap();
            assert_eq!(identifier.system, Some(format!("PROJECT_1_ID")));
            assert_ne!(identifier.value.as_ref(), Some(&data_request.exchange_id));
        };


        let condition_response = reqwest::Client::new()
            .get("http://localhost:8095/fhir/Condition")
            .send()
            .await.unwrap()
            .json::<Bundle>()
            .await.unwrap();

        assert!(!condition_response.entry.is_empty());
        for entry in condition_response.entry.iter() {
            assert_ne!(entry.is_none(), true);
            let ref condition = match entry.clone().unwrap().resource.unwrap() {
                Resource::Condition(condition) => condition,
                _ => continue,
            };
            // ensure identifier was changed to the project id
            let identifier = condition.subject.identifier.clone().unwrap();
            assert_eq!(identifier.system, Some(format!("PROJECT_1_ID")));
            assert_ne!(identifier.value.as_ref(), Some(&data_request.exchange_id));
        };
    }
}
