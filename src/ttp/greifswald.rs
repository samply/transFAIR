use std::str::FromStr;

use clap::Parser;
use fhirbolt::model::r4b::{
    Resource,
    resources::{
        Bundle, Consent, Parameters, ParametersParameter, ParametersParameterValue, Patient,
        QuestionnaireResponse,
    },
    types::{Coding, Identifier},
};
use tracing::debug;

use crate::fhir::ParameterExt;
use crate::{CONFIG, ttp_bail};
use crate::{
    config::ClientBuilderExt,
    fhir::{FhirRequestExt, FhirResponseExt},
};

use super::TtpError;

#[derive(Debug, Parser, Clone)]
pub struct GreifswaldConfig {
    #[clap(flatten)]
    pub base: super::TtpInner,

    #[clap(long = "ttp-gw-source", env = "TTP_GW_SOURCE")]
    source: String,

    #[clap(long = "ttp-gw-epix-domain", env = "TTP_GW_EPIX_DOMAIN")]
    epix_domain: String,

    #[clap(long = "ttp-gw-gpas-domain", env = "TTP_GW_GPAS_DOMAIN")]
    gpas_domain: String,
}

impl std::ops::Deref for GreifswaldConfig {
    type Target = super::TtpInner;

    fn deref(&self) -> &Self::Target {
        &self.base
    }
}

impl GreifswaldConfig {
    pub async fn check_availability(&self) -> bool {
        self.client.get(self.url.clone()).send().await.is_ok()
    }

    pub async fn check_idtype_available(&self, idtype: &str) -> bool {
        idtype == "https://ths-greifswald.de/fhir/gpas"
            || idtype.starts_with("https://ths-greifswald.de/fhir/epix/identifier/")
    }

    // https://www.ths-greifswald.de/wp-content/uploads/tools/fhirgw/ig/2024-3-0/ImplementationGuide-markdown-Einwilligungsmanagement-Operations-addConsent.html
    pub(super) async fn document_patient_consent(
        &self,
        _consent: Consent,
        patient: &Patient,
    ) -> Result<Consent, TtpError> {
        let url = self.url.join("/ttp-fhir/fhir/gics/$addConsent").unwrap();
        let params = Parameters {
            parameter: vec![
                ParametersParameter {
                    name: "patient".into(),
                    resource: Some(Resource::Patient(Box::new(patient.clone()))),
                    ..Default::default()
                },
                ParametersParameter {
                    name: "questionnaireResponse".into(),
                    resource: Some(Resource::QuestionnaireResponse(
                        QuestionnaireResponse {
                            status: "completed".into(),
                            ..Default::default()
                        }
                        .into(),
                    )),
                    ..Default::default()
                },
                ParametersParameter {
                    name: "domain".into(),
                    value: Some(ParametersParameterValue::String(
                        self.gpas_domain.clone().into(),
                    )),
                    ..Default::default()
                },
                // ParametersParameter::builder()
                //     .name("documentReference".into())
                //     .resource(
                //         DocumentReference::builder()
                //             // .content(vec![Some(DocumentReferenceContent::builder().attachment(a).build().unwrap())])
                //             // .content(vec![None])
                //             .contained(vec![consent.into()])
                //             .status(fhir_sdk::r4b::codes::DocumentReferenceStatus::Current)
                //             .build()
                //             .unwrap()
                //             .into(),
                //     )
                //     .build()
                //     .ok(),
            ],
            ..Default::default()
        };
        let res = self
            .client
            .post(url)
            .fhir_json(&params)?
            .add_auth(&self.ttp_auth)
            .await?
            .send()
            .await?;
        if let Err(e) = res.error_for_status_ref() {
            ttp_bail!(
                "Error while sending consent: {e:#}\nBody was: {}",
                res.text().await.unwrap_or_else(|e| e.to_string())
            );
        }
        let bundle = res.fhir_json::<Bundle>().await?;
        let Resource::Consent(c) = bundle
            .entry
            .iter()
            .next()
            .unwrap()
            .resource
            .as_ref()
            .cloned()
            .unwrap()
        else {
            ttp_bail!("Bundle did not contain a consent resoucrce: {bundle:#?}")
        };
        Ok(*c)
    }

    // https://www.ths-greifswald.de/wp-content/uploads/tools/fhirgw/ig/2024-3-0/ImplementationGuide-markdown-Pseudonymmanagement-Operations-pseudonymize.html
    pub(super) async fn request_project_pseudonym(
        &self,
        mut patient: Patient,
    ) -> Result<Patient, TtpError> {
        let url = self.url.join("ttp-fhir/fhir/epix/$addPatient").unwrap();
        let params = Parameters {
            parameter: vec![
                ParametersParameter {
                    name: "source".into(),
                    value: Some(ParametersParameterValue::String(self.source.clone().into())),
                    ..Default::default()
                },
                ParametersParameter {
                    name: "domain".into(),
                    value: Some(ParametersParameterValue::String(
                        self.epix_domain.clone().into(),
                    )),
                    ..Default::default()
                },
                ParametersParameter {
                    name: "forceReferenceUpdate".into(),
                    value: Some(ParametersParameterValue::Boolean(true.into())),
                    ..Default::default()
                },
                ParametersParameter {
                    name: "saveAction".into(),
                    value: Some(ParametersParameterValue::Coding(
                        Coding {
                            system: Some(
                                "https://ths-greifswald.de/fhir/CodeSystem/epix/SaveAction".into(),
                            ),
                            code: Some("DONT_SAVE_ON_PERFECT_MATCH".into()),
                            ..Default::default()
                        }
                        .into(),
                    )),
                    ..Default::default()
                },
                ParametersParameter {
                    name: "identity".into(),
                    resource: Some(Resource::Patient(
                        {
                            patient.identifier = Vec::new();
                            patient
                        }
                        .into(),
                    )),
                    ..Default::default()
                },
            ],
            ..Default::default()
        };
        let res = self
            .client
            .post(url)
            .fhir_json(&params)?
            .add_auth(&self.ttp_auth)
            .await?
            .send()
            .await?;
        if let Err(e) = res.error_for_status_ref() {
            ttp_bail!(
                "Error while matching patient: {e:#}\nBody was: {}",
                res.text().await.unwrap_or_else(|e| e.to_string())
            );
        }
        let parameters = res.fhir_json::<Parameters>().await?;
        debug!(?parameters);
        let Some(result) = parameters.parameter.get_param_by_name("matchResult") else {
            ttp_bail!("Response parameters did not contain a match result")
        };
        let status = result.part.get_param_by_name("matchStatus");
        let Some(ParametersParameterValue::Coding(c)) = status.and_then(|c| c.value.as_ref())
        else {
            ttp_bail!("Response did not contain a matchStatus")
        };
        let Some(status) = c
            .code
            .as_ref()
            .and_then(|v| v.value.as_ref()?.parse::<MatchStatus>().ok())
        else {
            ttp_bail!("Failed to parse coding as MatchStatus. Was {c:?}")
        };
        if status == MatchStatus::MatchError {
            ttp_bail!("Got a matching error: {result:#?}")
        }
        let Some(ParametersParameter {
            resource: Some(Resource::Person(person)),
            ..
        }) = result.part.get_param_by_name("mpiPerson").as_ref()
        else {
            ttp_bail!("Matching result did not contain a patient: {result:#?}")
        };
        let Some(ex_id) = person.identifier.iter().find_map(|i| {
            if cfg!(test) {
                i.system.as_ref()?.value.as_ref()?
                    == "https://ths-greifswald.de/fhir/epix/identifier/MPI"
            } else {
                i.system.as_ref()?.value.as_ref()? == &CONFIG.exchange_id_system
            }
            .then_some(i.value.as_ref()?.value.clone()?)
        }) else {
            ttp_bail!("Patient returned by matching did not contain a mpi identifier")
        };
        let mut idents = person.identifier.clone();
        idents.push(self.request_pseudonym(&ex_id).await?);
        Ok(Patient {
            id: person.id.clone(),
            identifier: idents,
            ..Default::default()
        })
    }

    async fn request_pseudonym(&self, ident: &str) -> Result<Identifier, TtpError> {
        let url = self
            .url
            .join("/ttp-fhir/fhir/gpas/$pseudonymizeAllowCreate")
            .unwrap();
        let params = Parameters {
            parameter: vec![
                ParametersParameter {
                    name: "target".into(),
                    value: Some(ParametersParameterValue::String(
                        self.gpas_domain.clone().into(),
                    )),
                    ..Default::default()
                },
                ParametersParameter {
                    name: "original".into(),
                    value: Some(ParametersParameterValue::String(ident.into())),
                    ..Default::default()
                },
            ],
            ..Default::default()
        };
        let res = self
            .client
            .post(url)
            .fhir_json(&params)?
            .add_auth(&self.ttp_auth)
            .await?
            .send()
            .await?;
        if let Err(e) = res.error_for_status_ref() {
            ttp_bail!(
                "Error requesting pseudonym: {e:#}\nBody was: {}",
                res.text().await.unwrap_or_else(|e| e.to_string())
            );
        }
        let parameters = res.fhir_json::<Parameters>().await?;
        debug!(?parameters, "Got pseudonym response");
        let Some(pseudonym) = parameters.parameter.get_param_by_name("pseudonym") else {
            ttp_bail!("Response parameters did not contain a pseudonym")
        };
        let Some(pseudonym_value) = pseudonym
            .part
            .get_param_by_name("pseudonym")
            .and_then(|v| v.value.as_ref())
        else {
            ttp_bail!("Response did not contain a pseudonym")
        };
        let ParametersParameterValue::Identifier(pseudonym_ident) = pseudonym_value else {
            ttp_bail!("Pseudonym was not an identifier")
        };
        Ok((**pseudonym_ident).clone())
    }
}

/// Taken from: https://simplifier.net/packages/ths-greifswald.ttp-fhir-gw/2024.1.1/files/2432769
#[derive(Debug, PartialEq)]
enum MatchStatus {
    ExternalMatch,
    Match,
    MatchError,
    MultipleMatch,
    NoMatch,
    PerfectMatch,
    PerfectMatchWithUpdate,
    PossibleMatch,
}

impl FromStr for MatchStatus {
    type Err = ();

    fn from_str(s: &str) -> Result<Self, Self::Err> {
        match s {
            "EXTERNAL_MATCH" => Ok(Self::ExternalMatch),
            "MATCH" => Ok(Self::Match),
            "MATCH_ERROR" => Ok(Self::MatchError),
            "MULTIPLE_MATCH" => Ok(Self::MultipleMatch),
            "NO_MATCH" => Ok(Self::NoMatch),
            "PERFECT_MATCH" => Ok(Self::PerfectMatch),
            "PERFECT_MATCH_WITH_UPDATE" => Ok(Self::PerfectMatchWithUpdate),
            "POSSIBLE_MATCH" => Ok(Self::PossibleMatch),
            _ => Err(()),
        }
    }
}

#[cfg(test)]
mod tests {
    use crate::{config::Auth, ttp::TtpInner};

    use super::*;
    use fhirbolt::model::r4b::types::{Address, HumanName};
    use reqwest::Client;

    #[tokio::test]
    #[ignore = "Unclear how we proceed here as it does not seem to accept a Consent resource"]
    async fn test_document_patient_consent() {
        let ttp = GreifswaldConfig {
            base: TtpInner {
                url: "https://demo.ths-greifswald.de".parse().unwrap(),
                project_id_system: "MII".into(),
                client: Client::new(),
                ttp_auth: Auth::None,
            },
            source: "dummy_safe_source".into(),
            epix_domain: "Demo".into(),
            gpas_domain: "MII".into(),
        };
        ttp.document_patient_consent(
            Consent {
                status: "active".into(),
                ..Default::default()
            },
            &fake_patient(),
        )
        .await
        .unwrap();
    }

    #[tokio::test]
    async fn test_request_project_pseudonym() {
        let ttp = GreifswaldConfig {
            base: TtpInner {
                url: "https://demo.ths-greifswald.de".parse().unwrap(),
                project_id_system: "Transferstelle A".into(),
                client: Client::new(),
                ttp_auth: Auth::None,
            },
            source: "dummy_safe_source".into(),
            epix_domain: "Demo".into(),
            gpas_domain: "Transferstelle A".into(),
        };
        dbg!(ttp.request_project_pseudonym(fake_patient()).await.unwrap());
    }

    fn fake_patient() -> Patient {
        Patient {
            name: vec![HumanName {
                given: vec!["Max".into()],
                family: Some("Mustermann".into()),
                ..Default::default()
            }],
            gender: Some("male".into()),
            birth_date: Some("1980-01-01".into()),
            address: vec![Address {
                city: Some("Speyer".into()),
                postal_code: Some("67346".into()),
                line: vec!["Foostr. 5".into()],
                ..Default::default()
            }],
            ..Default::default()
        }
    }
}
