use fhir_sdk::r4b::resources::Consent;
use reqwest::{header, StatusCode};
use tracing::{debug, error, warn};

pub async fn post_consent(
    fhir_endpoint: &String,
    consent: Consent,
) -> Result<Consent, (StatusCode, &'static str)> {
    let consent_endpoint = format!("{}/fhir/Consent", fhir_endpoint);
    debug!("Posting consent to {}", consent_endpoint);

    let response = reqwest::Client::new()
        .post(consent_endpoint)
        .header(header::CONTENT_TYPE, "application/json+fhir")
        .json(&consent)
        .send()
        .await
        .map_err(|err| {
            warn!("Unable to connnect to fhir consent server: {}", err);
            (StatusCode::SERVICE_UNAVAILABLE, "Unable to connect to consent fhir server. Please try later.")
        })
        .unwrap();

    if !response.status().is_success() {
        error!("Unable to create consent in consent server: status={}", response.status());
        return Err((StatusCode::BAD_GATEWAY, "Unable to create consent in consent server. Please contact your administrator."))
    }

    response.json::<Consent>() 
        .await
        .map_err(|err| {
            error!("Unable to parse consent returned by fhir server: {}", err);
            (StatusCode::BAD_GATEWAY, "Unable to parse consent returned by consent server. Please contact your administrator.")
        })
}
