use axum::{routing::{get, post}, Router};
use chrono::Duration;
use clap::Parser;
use config::Config;
use fhir::{get_mdat_as_bundle, put_mdat_as_bundle};
use once_cell::sync::Lazy;
use sqlx::SqlitePool;
use tracing::{debug, error, warn, Level};
use tracing_subscriber::{EnvFilter, util::SubscriberInitExt};

use crate::requests::{create_data_request, list_data_requests, get_data_request};

mod banner;
mod config;
mod fhir;
mod requests;
mod mainzelliste;

static CONFIG: Lazy<Config> = Lazy::new(Config::parse);
static SERVER_ADDRESS: &str = "0.0.0.0:8080";

#[tokio::main]
async fn main() {
    tracing_subscriber::FmtSubscriber::builder()
        .with_max_level(Level::DEBUG)
        .with_env_filter(EnvFilter::from_default_env())
        .finish()
        .init();
    banner::print_banner();
    debug!("{:#?}", Lazy::force(&CONFIG));

    let database_pool = SqlitePool::connect(CONFIG.database_url.as_str())
        .await.map_err(|e| {
            error!("Unable to connect to database file {}. Error is: {}", CONFIG.database_url.as_str(), e);
            return
        }).unwrap();
    
    let _ = sqlx::migrate!().run(&database_pool).await;

    // TODO: Verify that TTP is reachable
    // TODO: Check that the TTP provides CONFIG.ttp.project_id_system and CONFIG.exchange_id_system, otherwise refuse to start

    tokio::spawn(async move {
        if let Err(e) = process_data_requests().await {
            warn!("Failed to fetch project data: {e}. Will try again later");
        }
        loop {
            if let Err(e) = process_data_requests().await {
                warn!("Failed to fetch project data: {e}. Will try again later");
            }
            tokio::time::sleep(tokio::time::Duration::from_secs(60*60)).await;
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
}

async fn process_data_requests() -> Result<(), String> {
    let query_from_date = chrono::prelude::Utc::now() - Duration::days(1);
    let project_data = get_mdat_as_bundle(
               CONFIG.fhir_input_url.clone(), 
                query_from_date.naive_local().into()
            );
    let result = project_data.await.map_err(|err| {
            // TODO: write down error and safe it to corresponding data request
            error!("Unable to parse bundle returned by mdat server ({}): {}", CONFIG.fhir_input_url, err);
            err
        }).unwrap();
    if result.entry.is_empty() {
            debug!("Received empty bundle from mdat server ({}). No update necessary", CONFIG.fhir_input_url);
        } else {
            // TODO: error handling
            put_mdat_as_bundle(CONFIG.fhir_output_url.clone(), result).await;
        }
    Ok(())
}
