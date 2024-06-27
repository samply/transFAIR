// Client implementation for Mainzelliste TTP

use std::str::FromStr;

use fhir_sdk::r4b::{
    codes::AdministrativeGender,
    resources::{Consent, IdentifiableResource, Patient},
    types::{HumanName, Identifier},
};
use reqwest::StatusCode;
use serde::{Deserialize, Serialize};
use tracing::{debug, warn};

use crate::CONFIG;

pub async fn get_supported_ids() -> Result<Vec<String>, (StatusCode, &'static str)> {
    let idtypes_endpoint = CONFIG
        .institute_ttp_url
        .join("configuration/idTypes")
        .unwrap();
    let supported_ids = reqwest::Client::new()
        .get(idtypes_endpoint)
        .header("mainzellisteApiKey", &CONFIG.institute_ttp_api_key)
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

pub async fn create_project_pseudonym(
    patient: crate::requests::Patient,
) -> Result<Vec<Option<Identifier>>, (StatusCode, &'static str)> {
    let identifier_request = build_identifier_request(patient);

    let patients_endpoint = CONFIG.institute_ttp_url.join("fhir/Patient").unwrap();

    let response = reqwest::Client::new()
        .post(patients_endpoint)
        .header("mainzellisteApiKey", CONFIG.institute_ttp_api_key.clone())
        .json(&identifier_request)
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

    let patient_identifiers = patient.identifier.clone();

    Ok(patient_identifiers)
}

#[derive(Deserialize, Debug)]
struct Session {
    uri: String 
}
async fn create_mainzelliste_session() -> Result<Session, (StatusCode, &'static str)> {
    let sessions_endpoint = CONFIG.institute_ttp_url.join("sessions").unwrap();
    debug!("Requesting Session from Mainzelliste: {}", sessions_endpoint);
    reqwest::Client::new()
        .post(sessions_endpoint)
        .header("mainzellisteApiKey", &CONFIG.institute_ttp_api_key)
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

async fn create_mainzelliste_token(session: Session, token_type: TokenType) -> Result<Token, (StatusCode, &'static str)> {
    debug!("create_mainzelliste_token called with: session={:?} token_type={:?}", session, token_type);
    let tokens_endpoint = format!("{}tokens", session.uri);
    debug!("Requesting addConsent Token from Mainzelliste: {}", tokens_endpoint);
    let token_request = Token {
        id: None,
        token_type: token_type
    };
    reqwest::Client::new()
    .post(tokens_endpoint)
    .header("mainzellisteApiKey", &CONFIG.institute_ttp_api_key)
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
    identifiers: Vec<Option<Identifier>>,
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
    consent_with_identifiers.set_identifier(identifiers);

    debug!("{:?}", consent_with_identifiers);    

    let session = create_mainzelliste_session().await?; 
    
    let token = create_mainzelliste_token(session, TokenType::AddConsent).await?;

    // if token.id.is_none() {
    //     return Err((StatusCode:: INTERNAL_SERVER_ERROR, "Unable to create Token in Mainzelliste TTP"))
    // }

    let consent_endpoint = CONFIG.institute_ttp_url.join("fhir/Consent").unwrap();

    let response = reqwest::Client::new()
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

// maps the program input to a valid identifier request in fhir
fn build_identifier_request(patient: crate::requests::Patient) -> Patient {
    let identifier_requests = patient
        .identifiers
        .iter()
        .map(|identifier| {
            Some(
                Identifier::builder()
                    .r#use(fhir_sdk::r4b::codes::IdentifierUse::Temp)
                    .system(identifier.to_owned())
                    .build()
                    .unwrap(),
            )
        })
        .collect();

    let birth_date = fhir_sdk::Date::from_str(&patient.birth_date.to_string())
        .map_err(|err| {
            warn!("Couln't parse birthdate from input. Error war {}", err);
            (
                StatusCode::BAD_REQUEST,
                "Couln't parse birthdate from input.",
            )
        })
        .unwrap();

    let patient = Patient::builder()
        .active(false)
        .identifier(identifier_requests)
        .gender(AdministrativeGender::Male)
        .name(vec![Some(
            HumanName::builder()
                .r#use(fhir_sdk::r4b::codes::NameUse::Official)
                .family(patient.name.family)
                .given(vec![Some(patient.name.given)])
                .build()
                .unwrap(),
        )])
        .birth_date(birth_date)
        .build()
        .unwrap();

    debug!("Created following patient resource: {:?}", patient);
    patient
}
