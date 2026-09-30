//! Client implementation for Mainzelliste TTP
use fhir_sdk::r4b::{
    resources::{Consent, ConsentPolicy, Patient},
    types::Reference,
};
use reqwest::StatusCode;
use serde::{Deserialize, Serialize};
use tracing::{debug, trace, warn};

use crate::{fhir::PatientExt, ttp_bail, CLIENT};

use super::TtpError;

#[derive(Debug, clap::Args, Clone)]
pub struct MlConfig {
    #[clap(flatten)]
    pub base: super::TtpInner,

    #[clap(
        long = "ttp-ml-api-key",
        env = "TTP_ML_API_KEY"
    )]
    pub api_key: String,

    /// Identifier of the Mainzelliste consent template (Questionnaire) to document received consents against,
    /// for consents that reference a template of another TTP.
    #[clap(long = "ttp-ml-consent-template", env = "TTP_ML_CONSENT_TEMPLATE")]
    pub consent_template: Option<String>,

    /// Renames patient identifier systems before the patient is sent to Mainzelliste,
    /// as comma-separated `from=to` pairs, e.g. `KIS_ID=sapExtId`
    #[clap(
        long = "ttp-ml-id-mapping",
        env = "TTP_ML_ID_MAPPING",
        value_delimiter = ',',
        value_parser = parse_id_mapping,
    )]
    pub id_mapping: Vec<(String, String)>,
}

fn parse_id_mapping(pair: &str) -> Result<(String, String), String> {
    match pair.split_once('=') {
        Some((from, to)) if !from.trim().is_empty() && !to.trim().is_empty() => {
            Ok((from.trim().to_owned(), to.trim().to_owned()))
        }
        _ => Err(format!("expected `from=to`, got `{pair}`")),
    }
}

impl std::ops::Deref for MlConfig {
    type Target = super::TtpInner;

    fn deref(&self) -> &Self::Target {
        &self.base
    }
}

impl MlConfig {
    pub(super) async fn check_availability(&self) -> bool {
        let response = match CLIENT
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

        let supported_ids = CLIENT
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
        mut patient: Patient,
        exchange_id_system: &str,
    ) -> Result<Patient, TtpError> {
        for identifier in patient.identifier.iter_mut().flatten() {
            if let Some((_, to)) = self
                .id_mapping
                .iter()
                .find(|(from, _)| identifier.system.as_deref() == Some(from.as_str()))
            {
                identifier.system = Some(to.clone());
            }
        }
        let patient = patient
          .add_id_request(exchange_id_system.to_owned())
          .add_id_request(self.project_id_system.clone());
        // TODO: Need to ensure request for project pseudonym is included
        let patients_endpoint = self.url.join("fhir/Patient").unwrap();

        let response = CLIENT
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

        CLIENT
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
        CLIENT
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
        consent_with_identifiers.patient = Some(self.patient_reference(patient)?);
        if let Some(template) = &self.consent_template {
            let questionnaire_id = self.find_consent_template(template).await?;
            let policy = ConsentPolicy::builder()
                .uri(format!("fhir/Questionnaire/{questionnaire_id}"))
                .build()
                .map_err(|_| (StatusCode::INTERNAL_SERVER_ERROR, "Unable to build consent policy"))?;
            consent_with_identifiers.policy = vec![Some(policy)];
        }

        trace!("{:?}", consent_with_identifiers);

        let session = self.create_mainzelliste_session().await?;

        let token = self.create_mainzelliste_token(session, TokenType::AddConsent).await?;

        let consent_endpoint = self.url.join("fhir/Consent").unwrap();

        let response: reqwest::Response = CLIENT
            .post(consent_endpoint)
            .header("Authorization", format!("MainzellisteToken {}", token.id))
            .header("Content-Type", "application/fhir+json")
            .json(&consent_with_identifiers)
            .send()
            .await
            .map_err(|err| {
                warn!("Unable to add Consent to TTP: {}", err);
                (
                    StatusCode::BAD_GATEWAY,
                    "Failed to add Consent to TTP",
                )
            })?;

        let status = response.status();
        let text = response.text().await.unwrap_or_default();
        if !status.is_success() {
            warn!("Mainzelliste rejected the consent: status={status} text={text}");
            return Err((StatusCode::BAD_GATEWAY, "Mainzelliste rejected the consent"));
        }
        debug!("Response from TTP for Consent request: status={status} text={text}");

        Ok(())
    }

    /// Id of the Questionnaire whose identifier matches `template`.
    async fn find_consent_template(&self, template: &str) -> Result<String, (StatusCode, &'static str)> {
        let session = self.create_mainzelliste_session().await?;
        let token = self.create_mainzelliste_token(session, TokenType::SearchConsentTemplates).await?;
        let questionnaires = CLIENT
            .get(self.url.join("fhir/Questionnaire").unwrap())
            .header("Authorization", format!("MainzellisteToken {}", token.id))
            .send()
            .await
            .and_then(|response| response.error_for_status())
            .map_err(|err| {
                warn!("Unable to search consent templates in TTP: {err}");
                (StatusCode::BAD_GATEWAY, "Unable to search consent templates in TTP")
            })?
            .json::<serde_json::Value>()
            .await
            .map_err(|err| {
                warn!("Unable to parse consent templates from TTP: {err}");
                (StatusCode::BAD_GATEWAY, "Unable to parse consent templates from TTP")
            })?;
        // Mainzelliste ignores the identifier search parameter, so filter here.
        questionnaires["entry"]
            .as_array()
            .into_iter()
            .flatten()
            .map(|entry| &entry["resource"])
            .find(|resource| {
                resource["identifier"]
                    .as_array()
                    .is_some_and(|ids| ids.iter().any(|id| id["value"].as_str() == Some(template)))
            })
            .and_then(|resource| resource["id"].as_str())
            .map(str::to_owned)
            .ok_or_else(|| {
                warn!("TTP has no consent template with identifier {template}");
                (StatusCode::INTERNAL_SERVER_ERROR, "Configured consent template not found in TTP")
            })
    }

    /// Reference to the patient by its project pseudonym, as Mainzelliste expects it on a Consent.
    fn patient_reference(&self, patient: &Patient) -> Result<Reference, (StatusCode, &'static str)> {
        let mut identifier = patient
            .get_identifier(&self.project_id_system)
            .cloned()
            .ok_or((StatusCode::INTERNAL_SERVER_ERROR, "Patient has no project pseudonym to link the consent to"))?;
        // Mainzelliste only accepts absolute identifier systems of the form <mainzelliste url>/id/<id type>.
        identifier.system = Some(self.url.join(&format!("id/{}", self.project_id_system)).unwrap().to_string());
        Reference::builder()
            .identifier(identifier)
            .build()
            .map_err(|_| (StatusCode::INTERNAL_SERVER_ERROR, "Unable to build patient reference for consent"))
    }
}

#[derive(Serialize, Deserialize, Debug)]
#[serde(rename_all="camelCase")]
enum TokenType {
    // #[serde(with = "TokenType")] 
    AddConsent,
    SearchConsentTemplates,
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
