use chrono::NaiveDate;
use fhir_sdk::r4b::resources::{Bundle, Consent};
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

pub async fn get_mdat_as_bundle(fhir_endpoint: String, last_update: NaiveDate) -> Result<Bundle, String> {
    let bundle_endpoint = format!("{}/fhir/Bundle", fhir_endpoint);
    debug!("Sending request to bundle_endpoint: {}", bundle_endpoint);
    let query = vec![
        ("_lastUpdated", format!("gt{}", last_update))
    ];
    reqwest::Client::new()
        .get(bundle_endpoint)
        // TODO: Find out why this is not working
        .query(&query)
        .send()
        .await
        .map_err(|err| {
            error!("Unable to query data from mdat server: {}", err);
            format!("Unable to query data from mdat server")
        })
        .unwrap()
        .json::<Bundle>()
        .await
        .map_err(|err| {
            error!("Unable to parse bundle returned from mdat server: {}", err);
            format!("Unable to parse bundle returned from mdat server")
        })
}