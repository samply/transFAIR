use axum::{routing::{get, post}, Router};
use chrono::Duration;
use clap::Parser;
use config::{Config, ProjectConfig};
use fhir::get_mdat_as_bundle;
use once_cell::sync::Lazy;
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

    tokio::spawn(async move {
        if let Err(e) = fetch_project_data(&CONFIG.projects).await {
            warn!("Failed to fetch project data: {e}. Will try again later");
        }
        loop {
            if let Err(e) = fetch_project_data(&CONFIG.projects).await {
                warn!("Failed to fetch project data: {e}. Will try again later");
            }
            tokio::time::sleep(tokio::time::Duration::from_secs(60*60)).await;
        }
    });

    // request api endpoint
    let request_routes = Router::new()
        .route("/", post(create_data_request))
        .route("/", get(list_data_requests))
        .route("/:request_id", get(get_data_request));

    let app = Router::new()
        .nest("/requests", request_routes);

    let listener = tokio::net::TcpListener::bind(SERVER_ADDRESS).await.unwrap();
    axum::serve(listener, app)
        .await
        .unwrap();
}

async fn fetch_project_data(projects: &Vec<ProjectConfig>) -> Result<(), String> {
    let query_from_date = chrono::prelude::Utc::now() - Duration::days(1);

    // NOTE: can't get type annotation to work here
    let project_data: Vec<_> = projects.iter().map(|project| {
        get_mdat_as_bundle(project.mdat_fhir_url.clone(), query_from_date.naive_local().into())
    }).collect();
    for data in project_data {
        let result = data.await.map_err(|err| {
            // TODO: write down error and safe it to corresponding data request
            error!("Unable to parse bundle returned by mdat server: {}", err);
            err
        }).unwrap();
        if result.entry.is_empty() {
            debug!("Received empty bundle. No update necessary");
        }
        // TODO: Send Data to Project Server
    }
    Ok(())
}
