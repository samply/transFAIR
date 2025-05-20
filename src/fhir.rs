use anyhow::Context;
use chrono::NaiveDateTime;
use fhir_sdk::r4b::{
    codes::IdentifierUse,
    resources::{Bundle, BundleEntry, BundleEntryRequest, ParametersParameter, Patient, Resource},
    types::Identifier,
};
use reqwest::{header, Client, StatusCode, Url};
use tracing::{debug, error, warn};

use crate::{config::{Auth, ClientBuilderExt}, requests::DataRequestPayload, CONFIG};

#[derive(Clone, Debug)]
pub struct FhirServer {
    url: Url,
    auth: Auth,
    client: Client,
}

impl FhirServer {
    pub fn new(url: Url, auth: Auth) -> Self {
        Self { url, auth, client: CONFIG.client.clone()}
    }
    
    pub async fn post_data_request(
        &self,
        payload: DataRequestPayload
    ) -> Result<String, (StatusCode, &'static str)> {
        let bundle_endpoint = format!("{}fhir/Bundle", self.url);
        debug!("Posting request for DIC to {}", self.url);

        let bundle: Bundle = payload.into();

        let response = self.client
            .post(bundle_endpoint)
            .add_auth(&self.auth)
            .await
            .unwrap()
            .header(header::CONTENT_TYPE, "application/json+fhir")
            .json(&bundle)
            .send()
            .await
            .map_err(|err| {
                warn!("Unable to connect to fhir server: {}", err);
                (
                    StatusCode::SERVICE_UNAVAILABLE,
                    "Unable to connect to consent fhir server. Please try later.",
                )
            })?;

        if response.status().is_client_error() || response.status().is_server_error() {
            error!(
                "Unable to push request to server: status={}",
                response.status()
            );
            return Err((
                StatusCode::BAD_GATEWAY,
                "Unable to create consent in consent server. Please contact your administrator.",
            ));
        };

        let bundle = response.json::<Bundle>()
            .await
            .map_err(|err| {
                error!("Unable to parse consent returned by fhir server: {}", err);
                (StatusCode::BAD_GATEWAY, "Unable to parse consent returned by consent server. Please contact your administrator.")
            })?;

        Ok(bundle
            .id
            .clone()
            .expect("Consent Server returned bundle without id."))
    }

    // get data from fhir server that updated after a specified date
    pub async fn pull_new_data(&self, last_update: NaiveDateTime) -> anyhow::Result<Bundle> {
        let bundle_endpoint = format!("{}fhir/Bundle", self.url);
        debug!("Fetching new data from: {}", bundle_endpoint);
        let query = vec![("_lastUpdated", format!("gt{}", last_update.format("%Y-%m-%dT%H:%M:%S").to_string()))];
        let response = self.client
            .get(bundle_endpoint)
            .add_auth(&self.auth)
            .await?
            .query(&query)
            .send()
            .await
            .context("Unable to query data from input server")?;
        // TODO: Account for more samples using the bundels next link
        response
            .json::<Bundle>()
            .await
            .context("Unable to response from input server")
    }

    // post a fhir bundle to a specified fhir server
    pub async fn post_data(&self, bundle: &Bundle) -> anyhow::Result<reqwest::Response> {
        let bundle_endpoint = format!("{}fhir", self.url);
        debug!("Posting data to output fhir server: {}", bundle_endpoint);
        self.client
            .post(bundle_endpoint)
            .add_auth(&self.auth)
            .await?
            .json(&bundle)
            .send()
            .await
            .context("Unable to post data to output fhir server")
    }
}

pub trait PatientExt: Sized {
    fn pseudonymize(self) -> axum::response::Result<Self>;
    fn add_id_request(self, id: String) -> Self;
    fn get_identifier(&self, id_system: &str) -> Option<&Identifier>;
    fn get_identifier_mut(&mut self, id_system: &str) -> Option<&mut Identifier>;
}

impl PatientExt for Patient {
    fn add_id_request(mut self, id: String) -> Self {
        let request = Identifier::builder()
            .r#use(IdentifierUse::Secondary)
            .system(id)
            .build()
            .expect("Vailid identifier system");
        self.identifier.push(Some(request));
        self
    }

    fn get_identifier(&self, id_system: &str) -> Option<&Identifier> {
        self.identifier
            .iter()
            .flatten()
            .find(|x| x.system.as_deref() == Some(id_system))
    }

    fn get_identifier_mut(&mut self, id_system: &str) -> Option<&mut Identifier> {
        self.identifier
            .iter_mut()
            .flatten()
            .find(|x| x.system.as_deref() == Some(id_system))
    }

    fn pseudonymize(self) -> axum::response::Result<Self> {
        let Some(exchange_identifier) = self
            .identifier
            .iter()
            .find(|x| {
                x.as_ref().is_some_and(|y| y.system.as_deref() == Some(&CONFIG.exchange_id_system))
            }) else {
                return Err((
                    StatusCode::BAD_REQUEST,
                    format!("Request did not contain identifier of system {}", &CONFIG.exchange_id_system)
                ).into());
            };
        let pseudonymized_patient = Patient::builder()
            .identifier(vec![exchange_identifier.clone()])
            .build()
            .map_err(|err| {
                (
                    StatusCode::INTERNAL_SERVER_ERROR,
                    format!("Unable to create pseudonymized patient object {}", err),
                )
            })?;
        Ok(pseudonymized_patient)
    }
}

impl Into<Bundle> for DataRequestPayload {
    fn into(self) -> Bundle {
        let patient_entry = BundleEntry::builder()
            .resource(Resource::from(self.patient))
            .request(
                BundleEntryRequest::builder()
                    .method(fhir_sdk::r4b::codes::HTTPVerb::Post)
                    .url(String::from("/Patient"))
                    .build()
                    .unwrap(),
            )
            .build()
            .unwrap();

        let consent_entry = self.consent.map(|c| {
            BundleEntry::builder()
                .resource(Resource::from(c))
                .request(
                    BundleEntryRequest::builder()
                        .method(fhir_sdk::r4b::codes::HTTPVerb::Post)
                        .url(String::from("/Consent"))
                        .build()
                        .unwrap(),
                )
                .build()
                .unwrap()
        });

        Bundle::builder()
            .r#type(fhir_sdk::r4b::codes::BundleType::Transaction)
            .entry(vec![Some(patient_entry), consent_entry])
            .build()
            .unwrap()
    }
}

pub trait ParameterExt {
    fn get_param_by_name(&self, name: &str) -> Option<&ParametersParameter>;
}

impl ParameterExt for Vec<Option<ParametersParameter>> {
    fn get_param_by_name(&self, name: &str) -> Option<&ParametersParameter> {
        self.iter().flatten().find(|p| p.name == name)
    }
}

#[cfg(test)]
mod tests {
    use crate::fhir::PatientExt;
    use fhir_sdk::r4b::{codes::IdentifierUse, resources::Patient};

    #[test]
    fn add_id_request() {
        let mut patient = Patient::builder().build().unwrap();
        patient = patient.add_id_request("SOME_SYSTEM".to_string());
        let identifier = patient.identifier[0]
            .as_ref()
            .expect("Add id request didn't add an identifier to empty patient");
        // expect a new identifier with same system as requested
        assert_eq!(identifier.system, Some("SOME_SYSTEM".to_string()));
        // expect the new identifier to have secondary use
        assert_eq!(identifier.r#use, Some(IdentifierUse::Secondary));
        // expect the new identifier to not have a value
        assert_eq!(identifier.value, None);
    }
}
