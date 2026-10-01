use axum::{
    Json, Router,
    extract::State,
    http::{HeaderValue, StatusCode},
    routing::post,
};
use fhir_sdk::r4b::resources::{Bundle, Resource};
use reqwest::{Proxy, Url};
use serde::Serialize;
use sqlx::SqlitePool;
use tokio::sync::{mpsc, oneshot};

use crate::{
    SERVER_ADDRESS, config,
    fhir::FhirServer,
    requests::{DataRequest, DataRequestPayload, RequestStatus},
    ttp::mainzelliste::MlConfig,
};

#[derive(Debug, clap::Args)]
pub struct Config {
    #[clap(long, env)]
    database_url: Url,
    #[clap(long, env)]
    beam_connect_url: Url,
    #[clap(long, env)]
    beam_connect_app_id: String,
    #[clap(long, env)]
    beam_connect_api_key: String,
    #[clap(long, env)]
    dkfz_id_system: String,
    #[clap(long, env)]
    remote_transfair_url: Url,
    #[clap(long, env)]
    remote_fhir_server_url: Url,
    #[clap(long, env, default_value = "SESSION_ID")]
    remote_exchange_id_system: String,
    #[clap(long, env)]
    destination_fhir_server_url: Url,
    #[clap(long, env, default_value = "")]
    destination_fhir_server_auth: config::Auth,
    /// Local Mainzelliste that stores the project pseudonym the remote site assigns to a linked
    /// patient, as external ID PROJECT_ID_SYSTEM next to the patient's DKFZ_ID_SYSTEM ID
    #[clap(flatten)]
    ttp: MlConfig,
}

#[derive(Debug, Clone)]
struct AppState {
    db: SqlitePool,
    bc_client: reqwest::Client,
    destination: FhirServer,
    process_tx: mpsc::Sender<oneshot::Sender<anyhow::Result<ProcessResult>>>,
    config: &'static Config,
}

#[derive(Debug, Serialize)]
struct ProcessResult {
    processed: usize,
    failed: usize,
}

pub async fn dhki_main(config: Config) -> anyhow::Result<()> {
    let listener = tokio::net::TcpListener::bind(SERVER_ADDRESS).await?;
    let pool = SqlitePool::connect(config.database_url.as_str()).await?;
    let proxy_auth = HeaderValue::from_str(&format!(
        "ApiKey {} {}",
        config.beam_connect_app_id, config.beam_connect_api_key
    ))?;
    let proxy = Proxy::all(config.beam_connect_url.clone())?.custom_http_auth(proxy_auth);
    let bc_client = reqwest::Client::builder().proxy(proxy).build()?;
    sqlx::query(
        "CREATE TABLE IF NOT EXISTS pending_linkage_requests (\
         data_request_id TEXT PRIMARY KEY NOT NULL, \
         dkfz_id TEXT NOT NULL, exchange_id TEXT NOT NULL)",
    )
    .execute(&pool)
    .await?;

    let config: &'static _ = Box::leak(Box::new(config));
    let (process_tx, process_rx) = mpsc::channel(16);
    let state = AppState {
        bc_client,
        db: pool,
        destination: FhirServer::new(
            config.destination_fhir_server_url.clone(),
            config.destination_fhir_server_auth.clone(),
        ),
        process_tx,
        config,
    };
    let worker_state = state.clone();
    tokio::spawn(process_worker(worker_state, process_rx));

    axum::serve(
        listener,
        Router::new()
            .route("/request-linkage", post(register_linkage_request))
            .route("/process-pending", post(process_pending_handler))
            .with_state(state),
    )
    .with_graceful_shutdown(async { tokio::signal::ctrl_c().await.unwrap() })
    .await?;
    Ok(())
}

async fn register_linkage_request(
    State(state): State<AppState>,
    Json(mut payload): Json<DataRequestPayload>,
) -> Result<(StatusCode, Json<DataRequest>), (StatusCode, String)> {
    let identifiers = &mut payload.patient.0.identifier;
    let Some(id_index) = identifiers
        .iter()
        .flatten()
        .position(|identifier| identifier.system.as_deref() == Some(&state.config.dkfz_id_system))
    else {
        return Err((
            StatusCode::BAD_REQUEST,
            format!("Patient has no {} identifier", state.config.dkfz_id_system),
        ));
    };
    let dkfz_identifier = identifiers.remove(id_index).unwrap();
    let dkfz_id = dkfz_identifier
        .value
        .clone()
        .ok_or((StatusCode::BAD_REQUEST, "BK identifier has no value".into()))?;

    let consent = payload.consent.as_mut().ok_or((
        StatusCode::BAD_REQUEST,
        "A consent is required for linkage".into(),
    ))?;
    let consent_identifier = consent
        .patient
        .as_ref()
        .and_then(|reference| reference.identifier.as_ref());
    if consent_identifier.and_then(|id| id.system.as_deref())
        != Some(state.config.dkfz_id_system.as_str())
        || consent_identifier.and_then(|id| id.value.as_deref()) != Some(dkfz_id.as_str())
    {
        return Err((
            StatusCode::BAD_REQUEST,
            "Consent does not reference the patient's BK identifier".into(),
        ));
    }
    consent.patient = None;

    let remote_request = state
        .config
        .remote_transfair_url
        .join("requests")
        .map_err(internal_error)?;
    let response = state
        .bc_client
        .post(remote_request)
        .json(&payload)
        .send()
        .await
        .map_err(bad_gateway)?
        .error_for_status()
        .map_err(bad_gateway)?
        .json::<DataRequest>()
        .await
        .map_err(bad_gateway)?;

    sqlx::query(
        "INSERT INTO pending_linkage_requests (data_request_id, dkfz_id, exchange_id) \
         VALUES (?1, ?2, ?3)",
    )
    .bind(&response.id)
    .bind(&dkfz_id)
    .bind(&response.exchange_id)
    .execute(&state.db)
    .await
    .map_err(internal_error)?;

    // The linkage itself already succeeded remotely, so a failure here is only logged.
    if let Some(project_id) = &response.project_id {
        let ttp = &state.config.ttp;
        match ttp
            .add_external_id(
                &state.config.dkfz_id_system,
                &dkfz_id,
                &ttp.project_id_system,
                project_id,
            )
            .await
        {
            Ok(()) => tracing::info!(
                "Stored the {} of linkage request {} in the TTP",
                ttp.project_id_system,
                response.id
            ),
            Err(error) => tracing::error!(
                "Failed to store the {} of linkage request {} in the TTP: {error:#}",
                ttp.project_id_system,
                response.id
            ),
        }
    }

    Ok((StatusCode::CREATED, Json(response)))
}

async fn process_pending_handler(
    State(state): State<AppState>,
) -> Result<Json<ProcessResult>, (StatusCode, String)> {
    let (response_tx, response_rx) = oneshot::channel();
    state
        .process_tx
        .send(response_tx)
        .await
        .map_err(internal_error)?;
    response_rx
        .await
        .map_err(internal_error)?
        .map(Json)
        .map_err(internal_error)
}

async fn process_worker(
    state: AppState,
    mut commands: mpsc::Receiver<oneshot::Sender<anyhow::Result<ProcessResult>>>,
) {
    let mut interval = tokio::time::interval(std::time::Duration::from_hours(1));
    loop {
        tokio::select! {
            _ = interval.tick() => {
                if let Err(error) = check_pending(&state).await {
                    tracing::error!("Failed to check pending linkage requests: {error:#}");
                }
            }
            Some(response_tx) = commands.recv() => {
                let _ = response_tx.send(check_pending(&state).await);
            }
            else => break,
        }
    }
}

async fn check_pending(state: &AppState) -> anyhow::Result<ProcessResult> {
    let requests: Vec<(String, String, String)> = sqlx::query_as(
        "SELECT data_request_id, dkfz_id, exchange_id FROM pending_linkage_requests",
    )
    .fetch_all(&state.db)
    .await?;
    let mut result = ProcessResult {
        processed: 0,
        failed: 0,
    };
    let pending = requests.len();
    // A request that cannot be checked must not block the others.
    for (request_id, dkfz_id, exchange_id) in requests {
        match process_request(state, &request_id, &dkfz_id, &exchange_id).await {
            Ok(true) => {
                tracing::info!("Loaded the response of linkage request {request_id}");
                result.processed += 1;
            }
            Ok(false) => {}
            Err(error) => {
                tracing::error!("Failed to process linkage request {request_id}: {error:#}");
                result.failed += 1;
            }
        }
    }
    tracing::info!(
        "Checked {pending} pending linkage requests: {} processed, {} failed, {} still waiting",
        result.processed,
        result.failed,
        pending - result.processed - result.failed
    );
    Ok(result)
}

/// Loads the response of a finished linkage request into the destination store.
/// Returns whether the request was finished and removed from the pending list.
async fn process_request(
    state: &AppState,
    request_id: &str,
    dkfz_id: &str,
    exchange_id: &str,
) -> anyhow::Result<bool> {
    let status_url = state
        .config
        .remote_transfair_url
        .join(&format!("requests/{request_id}"))?;
    let request = state
        .bc_client
        .get(status_url)
        .send()
        .await?
        .error_for_status()?
        .json::<DataRequest>()
        .await?;
    match request.status {
        RequestStatus::Created => return Ok(false),
        RequestStatus::Error => {
            tracing::warn!(
                "Request {request_id} failed: {}",
                request.message.as_deref().unwrap_or("unknown reason")
            );
            return Ok(false);
        }
        RequestStatus::Success => {}
    }

    let mut search_url = state.config.remote_fhir_server_url.join("fhir/Bundle")?;
    search_url
        .query_pairs_mut()
        .append_pair("identifier", &format!("DATAREQUEST_ID|{request_id}"));
    let mut search_result = state
        .bc_client
        .get(search_url)
        .send()
        .await?
        .error_for_status()?
        .json::<Bundle>()
        .await?;
    let mut bundles = std::mem::take(&mut search_result.entry)
        .into_iter()
        .flatten()
        .filter_map(|entry| match entry.resource {
            Some(Resource::Bundle(bundle)) => Some(bundle),
            _ => None,
        });
    let mut bundle = bundles
        .next()
        .ok_or_else(|| anyhow::anyhow!("No response bundle found for {request_id}"))?;
    anyhow::ensure!(
        bundles.next().is_none(),
        "Multiple response bundles found for {request_id}"
    );
    replace_identifier(
        &mut bundle,
        &state.config.remote_exchange_id_system,
        exchange_id,
        &state.config.dkfz_id_system,
        dkfz_id,
    )?;
    state
        .destination
        .post_data(&bundle)
        .await?
        .error_for_status()?;
    sqlx::query("DELETE FROM pending_linkage_requests WHERE data_request_id = ?1")
        .bind(request_id)
        .execute(&state.db)
        .await?;
    Ok(true)
}

fn replace_identifier(
    bundle: &mut Bundle,
    exchange_id_system: &str,
    exchange_id: &str,
    dkfz_id_system: &str,
    dkfz_id: &str,
) -> anyhow::Result<()> {
    fn visit(
        value: &mut serde_json::Value,
        exchange_id_system: &str,
        exchange_id: &str,
        system: &str,
        value_id: &str,
    ) {
        match value {
            serde_json::Value::Object(object) => {
                if object.get("system").and_then(|value| value.as_str()) == Some(exchange_id_system)
                    && object.get("value").and_then(|value| value.as_str()) == Some(exchange_id)
                {
                    object.insert("system".into(), system.into());
                    object.insert("value".into(), value_id.into());
                }
                for value in object.values_mut() {
                    visit(value, exchange_id_system, exchange_id, system, value_id);
                }
            }
            serde_json::Value::Array(values) => {
                for value in values {
                    visit(value, exchange_id_system, exchange_id, system, value_id);
                }
            }
            _ => {}
        }
    }

    let mut value = serde_json::to_value(&*bundle)?;
    visit(
        &mut value,
        exchange_id_system,
        exchange_id,
        dkfz_id_system,
        dkfz_id,
    );
    *bundle = serde_json::from_value(value)?;
    Ok(())
}

fn internal_error(error: impl std::fmt::Display) -> (StatusCode, String) {
    (StatusCode::INTERNAL_SERVER_ERROR, error.to_string())
}

fn bad_gateway(error: impl std::fmt::Display) -> (StatusCode, String) {
    (StatusCode::BAD_GATEWAY, error.to_string())
}

#[cfg(test)]
mod tests {
    use super::replace_identifier;
    use fhir_sdk::r4b::resources::Bundle;

    #[test]
    fn replaces_exchange_identifiers_with_bk_id() {
        let mut bundle: Bundle = serde_json::from_value(serde_json::json!({
            "resourceType": "Bundle",
            "type": "transaction",
            "entry": [{
                "resource": {
                    "resourceType": "Condition",
                    "clinicalStatus": {"coding": [{"code": "active"}]},
                    "subject": {"identifier": {"system": "SESSION_ID", "value": "exchange"}}
                },
                "request": {"method": "POST", "url": "/Condition"}
            }]
        }))
        .unwrap();

        replace_identifier(&mut bundle, "SESSION_ID", "exchange", "DKFZ_BK_ID", "bk-1").unwrap();
        let value = serde_json::to_value(bundle).unwrap();
        assert_eq!(
            value["entry"][0]["resource"]["subject"]["identifier"],
            serde_json::json!({"system": "DKFZ_BK_ID", "value": "bk-1"})
        );
    }
}
