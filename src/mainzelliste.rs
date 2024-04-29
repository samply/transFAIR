// Client implementation for Mainzelliste TTP

use std::str::FromStr;

use fhir_sdk::r4b::{
    codes::AdministrativeGender,
    resources::Patient,
    types::{HumanName, Identifier},
};
use reqwest::StatusCode;
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

    let patients_endpoint = CONFIG
        .institute_ttp_url
        .join("fhir/Patient")
        .unwrap();

    let response = reqwest::Client::new()
        .post(patients_endpoint)
        .header("mainzellisteApiKey", CONFIG.institute_ttp_api_key.clone())
        .json(&identifier_request)
        .send()
        .await
        .map_err(|err| {
            warn!("Failed to communicate with mainzelliste: {}", err);
            (StatusCode::SERVICE_UNAVAILABLE, "Failed to communicate with mainzelliste: {}", err)
        }).unwrap();
    
    let patient = response.json::<Patient>().await.map_err(|err| {
        warn!("Couldn't parse mainzelliste response as json: {}", err);
        (StatusCode::INTERNAL_SERVER_ERROR, "Couldn't parse mainzelliste response as json: {}", err)
    }).unwrap();
    
    let patient_identifiers = patient.identifier.clone();

    Ok(patient_identifiers)
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

    let birth_date = fhir_sdk::Date::from_str(
        &patient.birth_date.to_string()
    ).map_err(|err| {
        warn!("Couln't parse birthdate from input. Error war {}", err);
        (StatusCode::BAD_REQUEST, "Couln't parse birthdate from input.")
    }).unwrap();

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
