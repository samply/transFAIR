use std::{process::ExitCode, time};

use axum::{routing::{get, post}, Router};
use chrono::Duration;
use clap::Parser;
use config::Config;
use fhir::FhirServer;
use once_cell::sync::Lazy;
use sqlx::SqlitePool;
use tracing::{debug, error, info, trace, warn, Level};
use tracing_subscriber::{EnvFilter, util::SubscriberInitExt};

use crate::requests::{create_data_request, list_data_requests, get_data_request};

mod banner;
mod config;
mod fhir;
mod requests;
mod ttp;

static CONFIG: Lazy<Config> = Lazy::new(Config::parse);
static INPUT_SERVER: Lazy<FhirServer> = Lazy::new(|| {
    FhirServer {
        url: CONFIG.fhir_input_url.clone(),
        credentials: CONFIG.fhir_input_credentials.clone()
    }
});
static OUTPUT_SERVER: Lazy<FhirServer> = Lazy::new(|| {
    FhirServer {
        url: CONFIG.fhir_output_url.clone(),
        credentials: CONFIG.fhir_output_credentials.clone()
    }
});
static SERVER_ADDRESS: &str = "0.0.0.0:8080";

trait CheckAvailability {
    async fn check_availability(&self) -> bool;
}

trait CheckIdTypeAvailable {
    async fn check_idtype_available(&self, idtype: &str) -> bool;
}

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
            tokio::time::sleep(time::Duration::from_secs(2)).await;
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

    tokio::spawn(async move {
        const RETRY_PERIOD: u64 = 60;
        loop {
            // TODO: Persist the updated data in the database
            match fetch_data().await {
                Ok(status) => info!("{}", status),
                Err(error) => warn!("Failed to fetch project data: {}. Will try again in {}", error, RETRY_PERIOD)
            }
            tokio::time::sleep(tokio::time::Duration::from_secs(RETRY_PERIOD)).await;
        }
    });

    // request api endpoint
    let request_routes = Router::new()
        .route("/", post(create_data_request))
        .route("/", get(list_data_requests))
        .route("/:request_id", get(get_data_request))
        .with_state(database_pool);

    let app = Router::new()
        .nest("/requests", request_routes);

    let listener = tokio::net::TcpListener::bind(SERVER_ADDRESS).await.unwrap();
    axum::serve(listener, app)
        .await
        .unwrap();

    ExitCode::from(0)
}

// Pull data from INPUT_FHIR and push it to OUTPUT_FHIR
async fn fetch_data() -> Result<String, String> {
    // TODO: Check if we can use a smarter logic to fetch all not fetched data
    let fetch_start_date = chrono::prelude::Utc::now();
    let query_from_date = fetch_start_date - Duration::days(1);
    let new_data = INPUT_SERVER.pull_new_data(
        query_from_date.naive_local().into()
    ).await?;
    if new_data.entry.is_empty() {
        debug!("Received empty bundle from mdat server ({}). No update necessary", CONFIG.fhir_input_url);
    } else {
        OUTPUT_SERVER.post_data( new_data).await?;
    }
    Ok(format!("Last fetch for new data executed at {}", fetch_start_date))
}

#[cfg(test)]
mod tests {
    use reqwest::StatusCode;

    use crate::requests::DataRequest;

    async fn post_data_request() -> DataRequest {
        let json = include_bytes!("../docs/examples/data_request.json");
        let response = reqwest::Client::new()
            .post(format!("http://localhost:8080/requests"))
            .json(&serde_json::from_slice::<serde_json::Value>(json).unwrap())
            .send()
            .await
            .unwrap();
        assert_eq!(response.status(), StatusCode::CREATED);

        response.json().await.unwrap()
    }

    #[tokio::test]
    async fn get_data_request() {
        let data_request = post_data_request().await;
        let response = reqwest::Client::new()
            .get(format!("http://localhost:8080/requests/{}", dbg!(data_request.id)))
            .send()
            .await
            .unwrap();

        assert_eq!(response.status(), StatusCode::OK);
    }
    
}