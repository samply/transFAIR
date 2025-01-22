use chrono::NaiveDate;
use fhir_sdk::r4b::{
    codes::IdentifierUse,
    resources::{Bundle, BundleEntry, BundleEntryRequest, Patient, Resource},
    types::Identifier,
};
use reqwest::{header, Client, StatusCode, Url};
use tracing::{debug, error, warn};

use crate::{config::Auth, requests::DataRequestPayload, CONFIG};

#[derive(Clone, Debug)]
pub struct FhirServer {
    url: Url,
    auth: Option<Auth>,
    client: Client,
}

trait ClientBuilderExt {
    fn add_auth(self, auth: &Option<Auth>) -> Self;
}

impl ClientBuilderExt for reqwest::RequestBuilder {
    fn add_auth(self, auth: &Option<Auth>) -> Self {
        let Some(auth) = auth else {
            return self
        };
        match auth {
            Auth::Basic { user, pw } => self.basic_auth(user, Some(pw)),
        }
    }
}

impl FhirServer {
    pub fn new(url: Url, auth: Option<Auth>) -> Self {
        Self { url, auth, client: Client::new() }
    }
    
    pub async fn post_data_request(
        &self,
        payload: DataRequestPayload
    ) -> Result<String, (StatusCode, &'static str)> {
        let bundle_endpoint = format!("{}fhir", self.url);
        debug!("Posting request for DIC to {}", self.url);

        let bundle: Bundle = payload.into();

        let response = self.client
            .post(bundle_endpoint)
            .add_auth(&self.auth)
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
    pub async fn pull_new_data(&self, last_update: NaiveDate) -> Result<Bundle, String> {
        let bundle_endpoint = format!("{}fhir/Bundle", self.url);
        debug!("Fetching new data from: {}", bundle_endpoint);
        let query = vec![("_lastUpdated", format!("gt{}", last_update))];
        let response = self.client
            .get(bundle_endpoint)
            .add_auth(&self.auth)
            .query(&query)
            .send()
            .await
            .map_err(|err| format!("Unable to query data from input server: {}", err))?;
        response
            .json::<Bundle>()
            .await
            .map_err(|err| format!("Unable to response from input server: {}", err))
    }

    // post a fhir bundle to a specified fhir server
    pub async fn post_data(&self, bundle: &Bundle) -> Result<reqwest::Response, String> {
        let bundle_endpoint = dbg!(format!("{}fhir", self.url));
        debug!("Posting data to output fhir server: {}", bundle_endpoint);
        self.client
            .post(bundle_endpoint)
            .add_auth(&self.auth)
            .json(dbg!(&bundle))
            .send()
            .await
            .map_err(|err| format!("Unable to post data to output fhir server: {}", err))
    }
}

pub trait PatientExt: Sized {
    fn pseudonymize(self) -> axum::response::Result<Self>;
    fn add_id_request(self, id: String) -> axum::response::Result<Self>;
    fn get_exchange_identifier(&self) -> Option<&Identifier>;
}

impl PatientExt for Patient {
    fn add_id_request(mut self, id: String) -> axum::response::Result<Self> {
        let request = Identifier::builder()
            .r#use(IdentifierUse::Secondary)
            .system(id)
            .build()
            .map_err(|err| {
                // TODO: Ensure that this will be a fatal error, as otherwise the linkage will not be possible
                error!(
                    "Unable to add token request to data request. See error message: {}",
                    err
                );
                (
                    StatusCode::INTERNAL_SERVER_ERROR,
                    "Unable to add token request to data request",
                )
            })
            .unwrap();
        self.identifier.push(Some(request));
        Ok(self)
    }

    fn get_exchange_identifier(&self) -> Option<&Identifier> {
        self.identifier
            .iter()
            .flatten()
            .find(|x| x.system.as_ref() == Some(&CONFIG.exchange_id_system))
    }

    fn pseudonymize(self) -> axum::response::Result<Self> {
        let id = self.id.clone().unwrap();
        let exchange_identifier_pos = self
            .identifier
            .iter()
            .position(|x| {
                x.clone()
                    .is_some_and(|y| y.system == Some(CONFIG.exchange_id_system.clone()))
            })
            .unwrap();
        let exchange_identifier = self.identifier.get(exchange_identifier_pos).cloned();
        let pseudonymized_patient = Patient::builder()
            .id(id)
            .identifier(vec![exchange_identifier.unwrap()])
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

        let consent_entry = BundleEntry::builder()
            .resource(Resource::from(self.consent))
            .request(
                BundleEntryRequest::builder()
                    .method(fhir_sdk::r4b::codes::HTTPVerb::Post)
                    .url(String::from("/Consent"))
                    .build()
                    .unwrap(),
            )
            .build()
            .unwrap();

        Bundle::builder()
            .r#type(fhir_sdk::r4b::codes::BundleType::Transaction)
            .entry(vec![Some(patient_entry), Some(consent_entry)])
            .build()
            .unwrap()
    }
}

#[cfg(test)]
mod tests {
    use crate::fhir::PatientExt;
    use fhir_sdk::r4b::{codes::IdentifierUse, resources::Patient};

    #[test]
    fn add_id_request() {
        let mut patient = Patient::builder().build().unwrap();
        patient = patient.add_id_request("SOME_SYSTEM".to_string()).unwrap();
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
