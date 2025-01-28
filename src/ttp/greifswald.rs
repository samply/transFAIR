use std::str::FromStr;

use fhir_sdk::r4b::resources::{Bundle, ParametersParameterValue, Resource};
use fhir_sdk::r4b::resources::{
    Consent, Parameters, ParametersParameter, Patient, QuestionnaireResponse,
};
use fhir_sdk::r4b::types::Coding;
use tracing::debug;

use crate::fhir::ParameterExt;
use crate::ttp_bail;

use super::{Ttp, TtpError};

pub async fn check_availability(ttp: &Ttp) -> bool {
    ttp.client.get(ttp.url.clone()).send().await.is_ok()
}

// https://www.ths-greifswald.de/wp-content/uploads/tools/fhirgw/ig/2024-3-0/ImplementationGuide-markdown-Pseudonymmanagement-Operations-pseudonymize.html ???????????
pub async fn check_idtype_available(_ttp: &Ttp, _idtype: &str) -> bool {
    // TODO: implement
    true
}

// https://www.ths-greifswald.de/wp-content/uploads/tools/fhirgw/ig/2024-3-0/ImplementationGuide-markdown-Einwilligungsmanagement-Operations-addConsent.html
pub async fn document_patient_consent(
    ttp: &Ttp,
    _consent: Consent,
    patient: &Patient,
) -> Result<Consent, TtpError> {
    let url = ttp.url.join("/ttp-fhir/fhir/gics/$addConsent").unwrap();
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
                    ttp.project_id_system.clone(),
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
    let bundle = ttp
        .client
        .post(url)
        .json(&params)
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
pub async fn request_project_pseudonym(ttp: &Ttp, patient: &Patient) -> Result<Patient, TtpError> {
    let url = ttp.url.join("ttp-fhir/fhir/epix/$addPatient").unwrap();
    let params = Parameters::builder()
        .parameter(vec![
            ParametersParameter::builder()
                .name("source".into())
                .value(ParametersParameterValue::String(String::from(
                    "dummy_safe_source",
                )))
                .build()
                .ok(),
            ParametersParameter::builder()
                .name("domain".into())
                .value(ParametersParameterValue::String(
                    ttp.project_id_system.clone(),
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
                        .system("https://ths-greifswald.de/fhir/CodeSystem/epix/SaveAction".into())
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
    let parameters = ttp
        .client
        .post(url)
        .json(&params)
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
    let Some(ParametersParameterValue::Coding(c)) = status.and_then(|c| c.value.as_ref()) else {
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
    if person.identifier.iter().flatten().count() == 0 {
        ttp_bail!("Patient returned by matching did not contain any identifiers")
    }
    // Do we need to copy over more fields?
    let patient = Patient::builder().identifier(person.identifier.clone()).build().unwrap();
    Ok(patient)
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
    use crate::ttp::TtpType;

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
        let ttp = Ttp {
            url: "https://demo.ths-greifswald.de".parse().unwrap(),
            api_key: String::new(),
            project_id_system: "MII".into(),
            ttp_type: TtpType::Greifswald,
            client: Client::new(),
        };
        document_patient_consent(
            &ttp,
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
        let ttp = Ttp {
            url: "https://demo.ths-greifswald.de".parse().unwrap(),
            api_key: String::new(),
            project_id_system: "Demo".into(),
            ttp_type: TtpType::Greifswald,
            client: Client::new(),
        };
        request_project_pseudonym(&ttp, &fake_patient())
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
