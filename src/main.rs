use std::{process::ExitCode, time::Duration};

use axum::{routing::{get, post}, Router};
use clap::Parser;
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
    // TODO: Check if we can use a smarter logic to fetch all not fetched data
    let fetch_start_date = dbg!(chrono::prelude::Utc::now());
    let query_from_date = fetch_start_date - chrono::Duration::days(1);
    let mut new_data = input_fhir_server.pull_new_data(
        query_from_date.naive_local().into()
    ).await?;
    if dbg!(new_data.entry.is_empty()) {
        debug!("Received empty bundle from mdat server ({}). No update necessary", CONFIG.fhir_input_url);
    } else {
        for entry in new_data.entry.iter_mut().flatten() {
            let Some(resource) = &mut entry.resource else {
                error!("Received invalid bundle for data request");
                continue;
            };

            let entry_bundle = match resource {
                Resource::Bundle(ref mut bundle) => bundle,
                _ => continue,
            };

            let Some(bundle_id) = entry_bundle.id.as_ref().cloned() else {
                error!("Received bundle without identifier. No link to data request is possible.");
                continue;
            };

            let mut linkage_results = None;
            if let Some(ttp) = &CONFIG.ttp {
                linkage_results = Some(replace_exchange_identifiers(&bundle_id, entry_bundle, ttp, &database_pool).await?);
            };

            // TODO: integrate transformation using transfair-batch here
            match output_fhir_server.post_data(&entry_bundle).await {
                Ok(response) => info!("Received a response: {}", response.text().await.as_deref().unwrap_or("<invalid text>")),
                Err(error) => error!("Received the following error: {error:#}"),
            };

            // FIXME: for some reason result is not written to database
            update_data_request(dbg!(&bundle_id), dbg!(linkage_results), &database_pool).await?;
        }

    }
    Ok(format!("Last fetch for new data executed at {}", fetch_start_date))
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
            Resource::Condition(ref mut condition) => &mut condition.subject,
            Resource::Procedure(ref mut procedure) => &mut procedure.subject,
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
        // TODO: Read value from database
        // TODO: Write project_id in database
        let result = sqlx::query!(
            "SELECT project_id FROM data_requests WHERE id = $1",
            data_request_identifier
        ).fetch_optional(database_connection).await?;
        if let Some(patient_identifier) = result {
            ident.value = Some(patient_identifier.project_id);
            Ok(Ok(rt))
        } else {
            Ok(Err(LinkageError::IdentifierNotLinkable(rt)))
        }
    }).collect::<TryJoinAll<_>>().await
}

#[cfg(test)]
mod tests {
    use fhir_sdk::r4b::resources::Bundle;
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
        let data_request = post_data_request().await;
        let bytes = include_bytes!("../docs/examples/example_input_data.json");
        let mut json = serde_json::from_slice::<Bundle>(bytes).unwrap();

        json.id = dbg!(Some(data_request.id.clone()));

        let response = reqwest::Client::new()
            .put(format!("http://localhost:8086/fhir/Bundle/{}", data_request.id.clone()))
            .json(&json)
            .header("Content-Type", "application/fhir+json")
            .send()
            .await
            .expect("POST to (/fhir/Bundle) should given a valid response");
        assert_eq!(response.status(), StatusCode::CREATED);
        let response_json = response.json::<Bundle>().await.unwrap();
        assert_eq!(response_json.id, Some(data_request.id.clone()))
    }

}
