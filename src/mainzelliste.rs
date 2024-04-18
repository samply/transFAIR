// Client implementation for Mainzelliste TTP

use reqwest::StatusCode;
use tracing::warn;

use crate::CONFIG;

pub async fn get_supported_ids() -> Result<Vec<String>, (StatusCode, &'static str)> {
    let idtypes_endpoint = CONFIG.institute_ttp_url.join("configuration/idTypes").unwrap();
    let supported_ids = reqwest::Client::new().get(idtypes_endpoint)
        .header("mainzellisteApiKey", &CONFIG.institute_ttp_api_key)
        .send()
        .await
        .map_err(|err| {
            warn!("Couldn't connect to Mainzelliste. Request failed with error: {}", err);
            (StatusCode::SERVICE_UNAVAILABLE, "Connection to TTP failed.")
        })?
        .json::<Vec<String>>()
        .await
        .map_err(|err| {
            warn!("Failed to parse returned idTypes from Mainzelliste. Failed with error: {}", err);
            (StatusCode::SERVICE_UNAVAILABLE, "Unable to parse Mainzelliste response as JSON")
        })?;
    Ok(supported_ids)
}
