use std::{process::ExitCode, time};

use axum::{routing::{get, post}, Router};
use chrono::Duration;
use clap::Parser;
use config::Config;
use fhir::{pull_new_data, post_data};
use once_cell::sync::Lazy;
use sqlx::SqlitePool;
use tracing::{debug, error, info, trace, warn, Level};
use tracing_subscriber::{EnvFilter, util::SubscriberInitExt};

use crate::requests::{create_data_request, list_data_requests, get_data_request};

mod banner;
mod config;
mod fhir;
mod requests;
mod mainzelliste;

static CONFIG: Lazy<Config> = Lazy::new(Config::parse);
static SERVER_ADDRESS: &str = "0.0.0.0:8080";

trait CheckAvailability {
    async fn check_availability(&self) -> bool;
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
        // TODO: Check that the TTP provides CONFIG.ttp.project_id_system and CONFIG.exchange_id_system, otherwise refuse to start
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
    let new_data = pull_new_data(
        &CONFIG.fhir_input_url, 
        query_from_date.naive_local().into()
    ).await?;
    if new_data.entry.is_empty() {
        debug!("Received empty bundle from mdat server ({}). No update necessary", CONFIG.fhir_input_url);
    } else {
        post_data(&CONFIG.fhir_output_url, new_data).await?;
    }
    Ok(format!("Last fetch for new data executed at {}", fetch_start_date))
}
