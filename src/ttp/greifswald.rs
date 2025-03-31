use std::str::FromStr;

use clap::Parser;
use fhir_sdk::r4b::resources::{Bundle, ParametersParameterValue, Resource};
use fhir_sdk::r4b::resources::{
    Consent, Parameters, ParametersParameter, Patient, QuestionnaireResponse,
};
use fhir_sdk::r4b::types::{Coding, Identifier};
use tracing::debug;

use crate::config::ClientBuilderExt;
use crate::fhir::ParameterExt;
use crate::ttp_bail;

use super::TtpError;

#[derive(Debug, Parser, Clone)]
pub struct GreifswaldConfig {
    #[clap(flatten)]
    pub base: super::TtpInner,

    #[clap(long = "ttp-gw-source", env = "TTP_GW_SOURCE")]
    source: String,

    #[clap(long = "ttp-gw-domain", env = "TTP_GW_DOMAIN")]
    matching_domain: String,
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

    // https://www.ths-greifswald.de/wp-content/uploads/tools/fhirgw/ig/2024-3-0/ImplementationGuide-markdown-Pseudonymmanagement-Operations-pseudonymize.html ???????????
    pub async fn check_idtype_available(&self, _idtype: &str) -> bool {
        // TODO: implement
        true
    }

    // https://www.ths-greifswald.de/wp-content/uploads/tools/fhirgw/ig/2024-3-0/ImplementationGuide-markdown-Einwilligungsmanagement-Operations-addConsent.html
    pub(super) async fn document_patient_consent(
        &self,
        _consent: Consent,
        patient: &Patient,
    ) -> Result<Consent, TtpError> {
        let url = self.url.join("/ttp-fhir/fhir/gics/$addConsent").unwrap();
        let params = Parameters::builder()
            .parameter(vec![
                ParametersParameter::builder()
                    .name("patient".into())
                    .resource(patient.clone().into())
                    .build()
                    .ok(),
                ParametersParameter::builder()
                    .name("questionnaireResponse".into())
                    .resource(
                        QuestionnaireResponse::builder()
                            .status(fhir_sdk::r4b::codes::QuestionnaireResponseStatus::Completed)
                            .build()
                            .unwrap()
                            .into(),
                    )
                    .build()
                    .ok(),
                ParametersParameter::builder()
                    .name("domain".into())
                    .value(ParametersParameterValue::String(
                        self.project_id_system.clone(),
                    ))
                    .build()
                    .ok(),
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
            ])
            .build()
            .unwrap();
        let bundle = self
            .client
            .post(url)
            .json(&params)
            .add_auth(&self.ttp_auth)
            .await?
            .send()
            .await?
            .error_for_status()?
            .json::<Bundle>()
            .await?;
        let Resource::Consent(c) = bundle
            .0
            .entry
            .iter()
            .flatten()
            .next()
            .unwrap()
            .resource
            .as_ref()
            .cloned()
            .unwrap()
        else {
            ttp_bail!("Bundle did not contain a consent resoucrce: {bundle:#?}")
        };
        Ok(c)
    }

    // https://www.ths-greifswald.de/wp-content/uploads/tools/fhirgw/ig/2024-3-0/ImplementationGuide-markdown-Pseudonymmanagement-Operations-pseudonymize.html
    pub(super) async fn request_project_pseudonym(
        &self,
        patient: &Patient,
    ) -> Result<Patient, TtpError> {
        let url = self.url.join("ttp-fhir/fhir/epix/$addPatient").unwrap();
        let params = Parameters::builder()
            .parameter(vec![
                ParametersParameter::builder()
                    .name("source".into())
                    .value(ParametersParameterValue::String(String::from(
                        self.source.clone(),
                    )))
                    .build()
                    .ok(),
                ParametersParameter::builder()
                    .name("domain".into())
                    .value(ParametersParameterValue::String(
                        self.matching_domain.clone(),
                    ))
                    .build()
                    .ok(),
                ParametersParameter::builder()
                    .name("forceReferenceUpdate".into())
                    .value(ParametersParameterValue::Boolean(true))
                    .build()
                    .ok(),
                ParametersParameter::builder()
                    .name("saveAction".into())
                    .value(
                        Coding::builder()
                            .system(
                                "https://ths-greifswald.de/fhir/CodeSystem/epix/SaveAction".into(),
                            )
                            .code("DONT_SAVE_ON_PERFECT_MATCH".into())
                            .build()
                            .map(ParametersParameterValue::Coding)
                            .unwrap(),
                    )
                    .build()
                    .ok(),
                ParametersParameter::builder()
                    .name("identity".into())
                    .resource(patient.clone().into())
                    .build()
                    .ok(),
            ])
            .build()
            .unwrap();
        let parameters = self
            .client
            .post(url)
            .json(&params)
            .add_auth(&self.ttp_auth)
            .await?
            .send()
            .await?
            .error_for_status()?
            .json::<Parameters>()
            .await?;
        debug!(?parameters);
        let Some(result) = parameters.parameter.get_param_by_name("matchResult") else {
            ttp_bail!("Response parameters did not contain a match result")
        };
        let status = result.part.get_param_by_name("matchStatus");
        let Some(ParametersParameterValue::Coding(c)) = status.and_then(|c| c.value.as_ref())
        else {
            ttp_bail!("Response did not contain a matchStatus")
        };
        let Some(status) = c.code.as_ref().and_then(|v| v.parse::<MatchStatus>().ok()) else {
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
        let Some(mpi_ident) = person
            .identifier
            .iter()
            .flatten()
            .find(|i| {
                i.system.as_deref() == Some("https://ths-greifswald.de/fhir/epix/identifier/MPI")
            })
            .and_then(|i| i.value.as_deref())
        else {
            ttp_bail!("Patient returned by matching did not contain a mpi identifier")
        };
        // Do we need to copy over more fields?
        let patient = Patient::builder()
            .identifier(vec![Some(self.request_pseudonym(mpi_ident).await?)])
            .build()
            .unwrap();
        Ok(patient)
    }

    async fn request_pseudonym(&self, ident: &str) -> Result<Identifier, TtpError> {
        let url = self
            .url
            .join("/ttp-fhir/fhir/gpas/$pseudonymizeAllowCreate")
            .unwrap();
        let params = Parameters::builder()
            .parameter(vec![
                ParametersParameter::builder()
                    .name("target".into())
                    .value(ParametersParameterValue::String(
                        self.project_id_system.clone(),
                    ))
                    .build()
                    .ok(),
                ParametersParameter::builder()
                    .name("original".into())
                    .value(ParametersParameterValue::String(ident.into()))
                    .build()
                    .ok(),
            ])
            .build()
            .unwrap();
        let parameters = self
            .client
            .post(url)
            .json(&params)
            .add_auth(&self.ttp_auth)
            .await?
            .send()
            .await?
            .error_for_status()?
            .json::<Parameters>()
            .await?;
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
        Ok(pseudonym_ident.clone())
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
    use fhir_sdk::{
        r4b::{
            codes::ConsentState,
            types::{Address, CodeableConcept, HumanName},
        },
        time::Date,
    };
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
            matching_domain: "Demo".into(),
        };
        ttp.document_patient_consent(
            Consent::builder()
                .status(ConsentState::Active)
                .scope(CodeableConcept::builder().build().unwrap())
                .category(vec![])
                .build()
                .unwrap(),
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
            matching_domain: "Demo".into(),
        };
        ttp.request_project_pseudonym(&fake_patient())
            .await
            .unwrap();
    }

    fn fake_patient() -> Patient {
        Patient::builder()
            .name(vec![Some(
                HumanName::builder()
                    .given(vec![Some("Max".into())])
                    .family("Mustermannerasdf".into())
                    .build()
                    .unwrap(),
            )])
            .gender(fhir_sdk::r4b::codes::AdministrativeGender::Male)
            .birth_date(fhir_sdk::Date::Date(
                Date::from_calendar_date(2015, fhir_sdk::time::Month::April, 1).unwrap(),
            ))
            .address(vec![Some(
                Address::builder()
                    .city("Speyer".into())
                    .postal_code("67346".into())
                    .line(vec![Some("Foostr. 5".into())])
                    .build()
                    .unwrap(),
            )])
            .build()
            .unwrap()
    }
}
