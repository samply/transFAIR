//! Client implementation for Mainzelliste TTP
use clap::Parser;
use fhir_sdk::r4b::{codes::IdentifierUse, resources::{Consent, IdentifiableResource, Patient}, types::{Identifier, Reference}};
use reqwest::StatusCode;
use serde::{Deserialize, Serialize};
use tracing::{debug, trace, warn, error};

use crate::{fhir::PatientExt, ttp_bail, CONFIG};

use super::TtpError;

#[derive(Debug, Parser, Clone)]
pub struct MlConfig {
    #[clap(flatten)]
    pub base: super::TtpInner,

    #[clap(
        long = "ttp-ml-api-key",
        env = "TTP_ML_API_KEY"
    )]
    pub api_key: String,
}

impl std::ops::Deref for MlConfig {
    type Target = super::TtpInner;

    fn deref(&self) -> &Self::Target {
        &self.base
    }
}

impl MlConfig {
    pub(super) async fn check_availability(&self) -> bool {
        let response = match self.client
            .get(self.url.clone())
            .header( "Accept", "application/json")
            .send()
            .await
        {
            Ok(response) => response,
            Err(e) => {
                debug!("Error making request to mainzelliste: {:?}", e);
                return false
            }
        };
        if response.status().is_client_error() || response.status().is_server_error() {
            return false;
        }
        true
    }

    pub(super) async fn check_idtype_available(&self, idtype: &str) -> bool {
        let ttp_supported_ids = match self.get_supported_ids()
            .await
            {
                Ok(idtypes) => idtypes,
                Err(err) => {
                    debug!("Error fetching supported id types from ttp: {:?}", err);
                    return false
                }
            };
        ttp_supported_ids.into_iter().any(
            |x| x == idtype
        )
    }

    pub async fn get_supported_ids(&self) -> Result<Vec<String>, (StatusCode, &'static str)> {
        let idtypes_endpoint = self.url.join("configuration/idTypes").unwrap();

        let supported_ids = self.client
            .get(idtypes_endpoint)
            .header("mainzellisteApiKey", &self.api_key)
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

    pub(super) async fn request_project_pseudonym(
        &self,
        patient: Patient,
    ) -> Result<Patient, TtpError> {
        let patient = patient
          .add_id_request(CONFIG.exchange_id_system.clone())
          .add_id_request(self.project_id_system.clone());
        // TODO: Need to ensure request for project pseudonym is included
        let patients_endpoint = self.url.join("fhir/Patient").unwrap();

        let response = self.client
            .post(patients_endpoint)
            .header("mainzellisteApiKey", &self.api_key)
            .json(&patient)
            .send()
            .await?;

        if let Err(err) = response.error_for_status_ref() {
            ttp_bail!("Error requesting project pseudonym from Mainzelliste: {err:#}\n Got response: {}", response.text().await?);
        }
        let patient = response
            .json::<Patient>()
            .await?;

        Ok(patient)
    }

    async fn create_mainzelliste_session(&self) -> Result<Session, (StatusCode, &'static str)> {
        let sessions_endpoint = self.url.join("sessions").unwrap();
        debug!("Requesting Session from Mainzelliste: {}", sessions_endpoint);

        self.client
            .post(sessions_endpoint)
            .header("mainzellisteApiKey", &self.api_key)
            .send()
            .await
            .map_err(|_| (StatusCode::INTERNAL_SERVER_ERROR, "Unable to create mainzelliste session. Ensure configured apiKey is valid."))
            .unwrap()
            .json::<Session>()
            .await
            .map_err(|_| (StatusCode::INTERNAL_SERVER_ERROR, "Unable to parse mainzelliste session."))
    }

    async fn create_mainzelliste_token(&self, session: Session, token_type: TokenType) -> Result<Token, (StatusCode, &'static str)> {
        debug!("create_mainzelliste_token called with: session={:?} token_type={:?}", session, token_type);
        let tokens_endpoint = format!("{}tokens", session.uri);
        debug!("Requesting addConsent Token from Mainzelliste: {}", tokens_endpoint);
        let token_request = TokenRequest {
            token_type
        };
        self.client
            .post(tokens_endpoint)
            .header("mainzellisteApiKey", &self.api_key)
            .json(&token_request)
            .send()
            .await
            .map_err(|err| {
                warn!("Unable to get token from mainzelliste: {}", err);
                (StatusCode::INTERNAL_SERVER_ERROR, "Unable to get Token from Mainzelliste")
            })
            .unwrap()
            .json::<Token>()
            .await
            .map_err(|err| {
                debug!("{:?}", err);
                warn!("Unable to parse token returned by mainzelliste: {}", err);
                (StatusCode::INTERNAL_SERVER_ERROR, "Unable to Parse Token from Mainzelliste: {}")
            })
    }

    pub(super) async fn document_patient_consent(
        &self,
        consent: &Consent,
        patient: &Patient,
    ) -> Result<(), (StatusCode, &'static str)> {
        if consent.patient.is_some() {
            warn!(
                "Received request with consent that already contained patient identifiers: {:?}",
                consent.patient
            );
            return Err((
                StatusCode::BAD_REQUEST,
                "Given Consent Resource already contained identifiers.",
            ));
        }

        let mut consent_with_identifiers = consent.clone();

        // let mut identifiers = patient.identifier.clone();
        // let identifiers_modified: Vec<Option<Identifier>> = identifiers.iter_mut().map(|identifier| {
        //     let Some(identifier) = identifier else {
        //         return None
        //     };
        //     identifier.system = match &mut identifier.system {
        //         Some(system) => Some(format!("{}pid/{}", &CONFIG.ttp.clone().expect("Running in mainzelliste and expecting ttp is configured").url, system)),
        //         None => None,
        //     };
        //     // TODO Search better way to do this
        //     Some(identifier.clone())
        // }).collect();

        let Some(exchange_identifier) = patient.get_identifier(&CONFIG.exchange_id_system) else {
            return Err((StatusCode::INTERNAL_SERVER_ERROR, "Unable to retrieve patient exchange identifier"))
        };
        let Some(exchange_identifier_value) = exchange_identifier.value.clone() else {
            return Err((StatusCode::INTERNAL_SERVER_ERROR, "Unable to retrieve patient exchange identifiers value"))
        };
        let patient_identifier = Identifier::builder()
            .system(format!("{}pid/{}", &CONFIG.ttp.clone().expect("Running in mainzelliste and expecting ttp is configured").url, &CONFIG.exchange_id_system))
            .value(exchange_identifier_value)
            .r#use(IdentifierUse::Official)
            .build().expect("Unable to create new FHIR Identifier for Consent");
        consent_with_identifiers.patient = Some(Reference::builder().identifier(patient_identifier).build().expect("Unable to construct patient reference in consent"));

        trace!("{:?}", consent_with_identifiers);

        let session = self.create_mainzelliste_session().await?;

        let token = self.create_mainzelliste_token(session, TokenType::AddConsent).await?;

        let consent_endpoint = self.url.join("fhir/Consent").unwrap();

        let response: reqwest::Response = self.client
            .post(consent_endpoint)
            .header("Authorization", format!("MainzellisteToken {}", token.id))
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

        if response.status().is_client_error() || response.status().is_server_error() {
            let error_status = response.status().clone();
            error!("Failed to add consent to Mainzelliste. Error is {}", response.text().await.expect("Mainzelliste returned not a text response"));
            return Err((
                error_status,
                "Failed to add consent to Mainzelliste"
            ))
        }

        Ok(())
    }
}

#[derive(Serialize, Deserialize, Debug)]
#[serde(rename_all="camelCase")]
enum TokenType {
    // #[serde(with = "TokenType")]
    AddConsent
}

#[derive(Serialize, Deserialize, Debug)]
struct Token {
    #[serde(rename = "tokenId")]
    id: String,
}

#[derive(Serialize, Deserialize, Debug)]
struct TokenRequest {
    #[serde(rename = "type")]
    token_type: TokenType
}

#[derive(Deserialize, Debug)]
struct Session {
    uri: String
}
