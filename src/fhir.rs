use anyhow::Context;
use chrono::NaiveDateTime;
use fhirbolt::{model::r4b::{
    resources::{Bundle, BundleEntry, BundleEntryRequest, ParametersParameter, Patient}, types::{Code, Identifier, Uri}, Resource
}, serde::{DeserializeResourceOwned, SerializeResource}};
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
            .json(&fhirbolt::serde::json::to_vec(&bundle, None).unwrap())
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

        let bundle = response.fhir_json::<Bundle>()
            .await
            .map_err(|err| {
                error!("Unable to parse consent returned by fhir server: {}", err);
                (StatusCode::BAD_GATEWAY, "Unable to parse consent returned by consent server. Please contact your administrator.")
            })?;

        Ok(bundle
            .id.and_then(|id| id.value)
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
            .fhir_json::<Bundle>()
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
            .fhir_json(bundle)?
            .send()
            .await
            .context("Unable to post data to output fhir server")
    }
}

pub trait FhirResponseExt {
    async fn fhir_json<T: DeserializeResourceOwned>(self) -> anyhow::Result<T>;
}

impl FhirResponseExt for reqwest::Response {
    async fn fhir_json<T: DeserializeResourceOwned>(self) -> anyhow::Result<T> {
        fhirbolt::serde::json::from_slice(&self.bytes().await?, None).map_err(Into::into)
    }
}

pub trait FhirRequestExt: Sized {
    fn fhir_json<T: SerializeResource>(self, input: &T) -> anyhow::Result<Self>;
}

impl FhirRequestExt for reqwest::RequestBuilder {
    fn fhir_json<T: SerializeResource>(self, input: &T) -> anyhow::Result<Self> {
        let rb = self
            .header(header::CONTENT_TYPE, header::HeaderValue::from_static("application/json"))
            .body(fhirbolt::serde::json::to_vec(input, None)?);
        Ok(rb)
    }
}

pub trait PatientExt: Sized {
    fn pseudonymize(self) -> axum::response::Result<Self>;
    fn add_id_request(self, id: String) -> Self;
    fn get_identifier(&self, id_system: &str) -> Option<&Identifier>;
    fn get_identifier_mut(&mut self, id_system: &str) -> Option<&mut Identifier>;
}

impl PatientExt for Patient {
    fn add_id_request(mut self, system: String) -> Self {
        let request = Identifier {
            r#use: Some("secondary".into()),
            system: Some(system.into()),
            ..Default::default()
        };
        self.identifier.push(request);
        self
    }

    fn get_identifier(&self, id_system: &str) -> Option<&Identifier> {
        self.identifier
            .iter()
            .find(|x| x.system.as_ref().and_then(|v| v.value.as_deref()) == Some(id_system))
    }

    fn get_identifier_mut(&mut self, id_system: &str) -> Option<&mut Identifier> {
        self.identifier
            .iter_mut()
            .find(|x| x.system.as_ref().and_then(|v| v.value.as_deref()) == Some(id_system))
    }

    fn pseudonymize(self) -> axum::response::Result<Self> {
        let Some(exchange_identifier) = self.get_identifier(&CONFIG.exchange_id_system) else {
            return Err((
                StatusCode::BAD_REQUEST,
                format!("Request did not contain identifier of system {}", &CONFIG.exchange_id_system)
            ).into());
        };
        let pseudonymized_patient = Patient {
            identifier: vec![exchange_identifier.clone()],
            ..Default::default()
        };
        Ok(pseudonymized_patient)
    }
}

impl Into<Bundle> for DataRequestPayload {
    fn into(self) -> Bundle {
        let patient_entry = BundleEntry {
            resource: Some(Resource::Patient(self.patient.into())),
            request: Some(BundleEntryRequest {
                method: Code::from("POST"),
                url: Uri::from("/Patient"),
                ..Default::default()
            }),
            ..Default::default()
        };

        let consent_entry = self.consent.map(|c| {
            BundleEntry {
                resource: Some(Resource::Consent(c.into())),
                request: Some(BundleEntryRequest {
                    method: Code::from("POST"),
                    url: Uri::from("/Consent"),
                    ..Default::default()
                }),
                ..Default::default()
            }
        });

        Bundle {
            r#type: "transaction".into(),
            entry: consent_entry.into_iter().chain(std::iter::once(patient_entry)).rev().collect(),
            ..Default::default()
        }
    }
}

pub trait ParameterExt {
    fn get_param_by_name(&self, name: &str) -> Option<&ParametersParameter>;
}

impl ParameterExt for Vec<ParametersParameter> {
    fn get_param_by_name(&self, name: &str) -> Option<&ParametersParameter> {
        self.iter().find(|p| p.name.value.as_deref() == Some(name))
    }
}

pub mod fhir_json {
    use fhirbolt::serde::{DeserializeResource, SerializeResource };
    use serde::{de::DeserializeSeed, Deserializer, Serialize, Serializer};

    
    pub fn serialize<T: SerializeResource, S: Serializer>(input: &T, ser: S) -> Result<S::Ok, S::Error> {
        T::serialization_context(input, Default::default(), unsafe {
            std::mem::transmute(0_u8)
        })
            .serialize(ser)
    }

    pub fn deserialize<'de, T: DeserializeResource<'de>, D: Deserializer<'de>>(deser: D) -> Result<T, D::Error> {
        T::deserialization_context(Default::default(), unsafe {
            std::mem::transmute(0_u8)
        })
            .deserialize(deser)
    }

    pub mod option {
        use serde::de::Visitor;

        use super::*;

        pub fn serialize<T: SerializeResource, S: Serializer>(input: &Option<T>, ser: S) -> Result<S::Ok, S::Error> {
            match input {
                Some(value) => super::serialize(value, ser),
                None => ser.serialize_none(),
            }
        }

        pub fn deserialize<'de, T: DeserializeResource<'de>, D: Deserializer<'de>>(deser: D) -> Result<Option<T>, D::Error> {
            struct OptionVisitor<'de, T: DeserializeResource<'de>> {
                context: T::Context,
            }
            impl<'de, T: DeserializeResource<'de>> Visitor<'de> for OptionVisitor<'de, T> {
                type Value = Option<T>;

                fn expecting(&self, formatter: &mut std::fmt::Formatter) -> std::fmt::Result {
                    formatter.write_str("an optional FHIR resource")
                }

                fn visit_none<E>(self) -> Result<Self::Value, E>
                where
                    E: serde::de::Error,
                {
                    Ok(None)
                }

                fn visit_some<D>(self, deserializer: D) -> Result<Self::Value, D::Error>
                where
                    D: Deserializer<'de>,
                {
                    self.context.deserialize(deserializer).map(Some)
                }
            }
            deser.deserialize_option(OptionVisitor {
                context: T::deserialization_context(Default::default(), unsafe {
                    std::mem::transmute(0_u8)
                }),
            })
        }

        // #[test]
        // fn deser_fhir_option() -> anyhow::Result<()> {
        //     use fhirbolt::model::r4b::types::String;
        //     #[derive(serde::Deserialize)]
        //     struct TestOption {
        //         #[serde(with = "option")]
        //         value: Option<String>,
        //     }
        //     let a: TestOption = serde_json::from_str(r#"{"value": "test"}"#)?;
        //     Ok(())
        // }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn add_id_request() {
        let mut patient = Patient::default();
        patient = patient.add_id_request("SOME_SYSTEM".to_string());
        let identifier = &patient.identifier[0];
        // expect a new identifier with same system as requested
        assert_eq!(identifier.system, Some("SOME_SYSTEM".into()));
        // expect the new identifier to have secondary use
        assert_eq!(identifier.r#use, Some("secondary".into()));
        // expect the new identifier to not have a value
        assert_eq!(identifier.value, None);
    }
}
