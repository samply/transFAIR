// Client implementation for Mainzelliste TTP
use fhir_sdk::r4b::resources::{Consent, IdentifiableResource, Patient};
use reqwest::StatusCode;
use serde::{Deserialize, Serialize};
use tracing::{debug, warn};

use crate::config::Ttp;

pub async fn get_supported_ids(ttp: &Ttp) -> Result<Vec<String>, (StatusCode, &'static str)> {
    let idtypes_endpoint = ttp.url.join("configuration/idTypes").unwrap();

    let supported_ids = reqwest::Client::new()
        .get(idtypes_endpoint)
        .header("mainzellisteApiKey", &ttp.api_key)
        .send()
        .await
        .map_err(|err| {
            warn!(
                "Couldn't connect to Mainzelliste. Request failed with error: {}",
                err
            );
            (StatusCode::SERVICE_UNAVAILABLE, "Connection to TTP failed.")
        })?
        .json::<Vec<String>>()
        .await
        .map_err(|err| {
            warn!(
                "Failed to parse returned idTypes from Mainzelliste. Failed with error: {}",
                err
            );
            (
                StatusCode::SERVICE_UNAVAILABLE,
                "Unable to parse Mainzelliste response as JSON",
            )
        })?;
    Ok(supported_ids)
}

pub async fn request_project_pseudonym(
    patient: &mut Patient,
    ttp: &Ttp
) -> Result<Patient, (StatusCode, &'static str)> {
    // TODO: Need to ensure request for project pseudonym is included
    let patients_endpoint = ttp.url.join("fhir/Patient").unwrap();

    let response = reqwest::Client::new()
        .post(patients_endpoint)
        .header("mainzellisteApiKey", &ttp.api_key.clone())
        .json(&patient)
        .send()
        .await
        .map_err(|err| {
            warn!("Failed to communicate with mainzelliste: {}", err);
            (
                StatusCode::SERVICE_UNAVAILABLE,
                "Failed to communicate with mainzelliste: {}",
                err,
            )
        })
        .unwrap();

    let patient = response
        .json::<Patient>()
        .await
        .map_err(|err| {
            warn!("Couldn't parse mainzelliste response as json: {}", err);
            (
                StatusCode::INTERNAL_SERVER_ERROR,
                "Couldn't parse mainzelliste response as json: {}",
                err,
            )
        })
        .unwrap();

    Ok(patient)
}

#[derive(Deserialize, Debug)]
struct Session {
    uri: String 
}
async fn create_mainzelliste_session(ttp: &Ttp) -> Result<Session, (StatusCode, &'static str)> {
    let sessions_endpoint = ttp.url.join("sessions").unwrap();
    debug!("Requesting Session from Mainzelliste: {}", sessions_endpoint);

    reqwest::Client::new()
        .post(sessions_endpoint)
        .header("mainzellisteApiKey", &ttp.api_key)
        .send()
        .await
        .map_err(|_| (StatusCode::INTERNAL_SERVER_ERROR, "Unable to create mainzelliste session. Ensure configured apiKey is valid."))
        .unwrap()
        .json::<Session>()
        .await
        .map_err(|_| (StatusCode::INTERNAL_SERVER_ERROR, "Unable to parse mainzelliste session."))
}

#[derive(Serialize, Deserialize, Debug)]
#[serde(rename_all="camelCase")]
enum TokenType {
    // #[serde(with = "TokenType")] 
    AddConsent
}

#[derive(Serialize, Deserialize, Debug)]
struct Token {
    id: Option<String>,
    #[serde(rename = "type")]
    token_type: TokenType
}

async fn create_mainzelliste_token(session: Session, token_type: TokenType, ttp: &Ttp) -> Result<Token, (StatusCode, &'static str)> {
    debug!("create_mainzelliste_token called with: session={:?} token_type={:?}", session, token_type);
    let tokens_endpoint = format!("{}tokens", session.uri);
    debug!("Requesting addConsent Token from Mainzelliste: {}", tokens_endpoint);
    let token_request = Token {
        id: None,
        token_type: token_type
    };
    reqwest::Client::new()
    .post(tokens_endpoint)
    .header("mainzellisteApiKey", &ttp.api_key)
    .json(&token_request)
    .send()
    .await
    .map_err(|err| {
        warn!("Unable to get token from mainzelliste: {}", err);
        (StatusCode::INTERNAL_SERVER_ERROR, "Unable to get Token from Mainzelliste")
    })
    .unwrap()
    // .json::<Token>()
    .text()
    .await
    .map(|res| {
        warn!("This token was returned by the mainzelliste: {:?}", res);
        Token {
            id: Some(format!("bla")),
            token_type: TokenType::AddConsent
        }
    })
    .map_err(|err| {
        warn!("Unable to parse token returned by mainzelliste: {}", err);
        (StatusCode::INTERNAL_SERVER_ERROR, "Unable to Parse Token from Mainzelliste: {}")
    }) 
}

pub async fn document_patient_consent(
    consent: Consent,
    patient: &Patient,
    ttp: &Ttp
) -> Result<Consent, (StatusCode, &'static str)> {
    if consent.patient.is_some() {
        warn!(
            "Received request with consent that already contained patient identifiers: {:?}",
            consent.identifier
        );
        return Err((
            StatusCode::BAD_REQUEST,
            "Given Consent Resource already contained identifiers.",
        ));
    }

    let mut consent_with_identifiers = consent.clone();
    consent_with_identifiers.set_identifier(patient.identifier.clone());

    debug!("{:?}", consent_with_identifiers);    

    let session = create_mainzelliste_session(&ttp).await?; 
    
    let token = create_mainzelliste_token(session, TokenType::AddConsent, &ttp).await?;

    // if token.id.is_none() {
    //     return Err((StatusCode:: INTERNAL_SERVER_ERROR, "Unable to create Token in Mainzelliste TTP"))
    // }

    let consent_endpoint = ttp.url.join("fhir/Consent").unwrap();

    let response: reqwest::Response = reqwest::Client::new()
        .post(consent_endpoint)
        .header("Authorization", format!("MainzellisteToken {}", token.id.unwrap()))
        .header("Content-Type", "application/fhir+json")
        .json(&consent_with_identifiers)
        .send()
        .await
        .map_err(|err| {
            warn!("Unable to add Consent to TTP: {}", err);
            (
                StatusCode::INTERNAL_SERVER_ERROR,
                "Failed to add Consent to TTP",
            );
        })
        .unwrap();

    debug!("Response from TTP for Consent request: status={} text={}", response.status(), response.text().await.unwrap());

    // let result = response
    //     .json::<Consent>()
    //     .await
    //     .map_err(|err| {
    //         warn!("Unable to parse Consent returned by TTP as JSON: {}", err);
    //         (
    //             StatusCode::INTERNAL_SERVER_ERROR,
    //             "Unable to parse Consent returned by TTP as JSON",
    //         )
    //     })
    //     .unwrap();

    Ok(consent)
}
