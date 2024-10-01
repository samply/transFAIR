use chrono::NaiveDate;
use fhir_sdk::r4b::resources::{Bundle, BundleEntry, BundleEntryRequest, Consent, Patient, Resource};
use reqwest::{header, StatusCode, Url};
use tracing::{debug, error, warn};

pub async fn post_data_request(
    fhir_endpoint: &Url,
    _fhir_api_key: &String,
    patient: Patient,
    consent: Consent
) -> Result<String, (StatusCode, &'static str)> {
    let bundle_endpoint = format!("{}fhir", fhir_endpoint);
    debug!("Posting request for DIC to {}", fhir_endpoint);

    let patient_entry = BundleEntry::builder()
        .resource(Resource::from(patient))
        .request(
            BundleEntryRequest::builder()
                .method(fhir_sdk::r4b::codes::HTTPVerb::Post)
                .url(String::from("/Patient"))
                .build().unwrap()
            ).build().unwrap();

    let consent_entry = BundleEntry::builder()
        .resource(Resource::from(consent))
        .request(
            BundleEntryRequest::builder()
            .method(fhir_sdk::r4b::codes::HTTPVerb::Post)
            .url(String::from("/Consent"))
            .build().unwrap()
        ).build().unwrap();

    let payload_bundle = Bundle::builder()
        .r#type(fhir_sdk::r4b::codes::BundleType::Transaction)
        .entry(
            vec![
                Some(patient_entry),
                Some(consent_entry)
            ]
        ).build().unwrap();

    let response = reqwest::Client::new().post(bundle_endpoint)
    .header(header::CONTENT_TYPE, "application/json+fhir")
    .json(&payload_bundle)
    .send()
    .await.map_err(|err | {
        warn!("Unable to connect to fhir server: {}", err);
        (StatusCode::SERVICE_UNAVAILABLE, "Unable to connect to consent fhir server. Please try later.")
    }).unwrap();

    if response.status().is_client_error() | response.status().is_server_error() {
        error!("Unable to push request to server: status={}", response.status());
        return Err((StatusCode::BAD_GATEWAY, "Unable to create consent in consent server. Please contact your administrator."))
    };

    let bundle = response.json::<Bundle>()
        .await
        .map_err(|err| {
            error!("Unable to parse consent returned by fhir server: {}", err);
            (StatusCode::BAD_GATEWAY, "Unable to parse consent returned by consent server. Please contact your administrator.")
        }).unwrap(); 

    Ok(bundle.id.clone().expect("Consent Server returned bundle without id."))
}

// get data from fhir server that updated after a specified date
pub async fn pull_new_data(fhir_endpoint: &Url, last_update: NaiveDate) -> Result<Bundle, String> {
    let bundle_endpoint = format!("{}fhir/Bundle", fhir_endpoint);
    debug!("Fetching new data from: {}", bundle_endpoint);
    let query = vec![
        ("_lastUpdated", format!("gt{}", last_update))
    ];
    let response = reqwest::Client::new()
        .get(bundle_endpoint)
        .query(&query)
        .send()
        .await
        .map_err(|err| {
            format!("Unable to query data from input server: {}", err)
        })?;
        response.json::<Bundle>()
        .await
        .map_err(|err| {
            format!("Unable to response from input server: {}", err)
        })
}

// post a fhir bundle to a specified fhir server
pub async fn post_data(fhir_endpoint: &Url, bundle: Bundle) -> Result<reqwest::Response, String> {
    let bundle_endpoint = format!("{}fhir", fhir_endpoint);
    debug!("Posting data to output fhir server: {}", bundle_endpoint);
    reqwest::Client::new()
        .post(bundle_endpoint)
        .json(&bundle)
        .send()
        .await
        .map_err(|err| {
            format!("Unable to post data to output fhir server: {}", err)
        })
}
