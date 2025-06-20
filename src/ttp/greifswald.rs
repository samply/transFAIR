use std::fmt::Display;
use std::str::FromStr;

use clap::Parser;
use fhir_sdk::r4b::codes::AdministrativeGender;
use fhir_sdk::r4b::resources::{Bundle, ParametersParameterValue, Resource};
use fhir_sdk::r4b::resources::{
    Consent, Parameters, ParametersParameter, Patient, QuestionnaireResponse,
};
use fhir_sdk::r4b::types::Identifier;

use crate::config::ClientBuilderExt;
use crate::ttp_bail;

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
        idtype == "https://ths-greifswald.de/fhir/gpas" || idtype.starts_with("https://ths-greifswald.de/fhir/epix/identifier/")
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
                        self.gpas_domain.clone(),
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
        let res = self
            .client
            .post(url)
            .json(&params)
            .add_auth(&self.ttp_auth)
            .await?
            .send()
            .await?;
        if let Err(e) = res.error_for_status_ref() {
            ttp_bail!("Error while sending consent: {e:#}\nBody was: {}", res.text().await.unwrap_or_else(|e| e.to_string()));
        }
        let bundle = res
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
        patient: Patient,
    ) -> Result<Patient, TtpError> {
        let url = self.url.join("epix/epixService").unwrap();
        let Self { epix_domain, source, .. } = self;
        let patient_xml = patient_to_xml(&patient);
        let soap_body = format!(r#"<?xml version="1.0" encoding="utf-8"?>
            <soap:Envelope
                xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/"
                xmlns:ser="http://service.epix.ttp.icmvc.emau.org/">
            <soap:Header/>
            <soap:Body>
                <ser:requestMPIWithConfig>
                    <domainName>{epix_domain}</domainName>
                    <identity>
                        {patient_xml}
                    </identity>
                    <sourceName>{source}</sourceName>
                    <requestConfig>
                        <forceReferenceUpdate>false</forceReferenceUpdate>
                        <saveAction>DONT_SAVE_ON_PERFECT_MATCH</saveAction>
                    </requestConfig>
                </ser:requestMPIWithConfig>
            </soap:Body>
            </soap:Envelope>"#);
        let res = self
            .client
            .post(url)
            .body(soap_body)
            .add_auth(&self.ttp_auth)
            .await?
            .send()
            .await?;
        if let Err(e) = res.error_for_status_ref() {
            ttp_bail!("Error while matching patient: {e:#}\nBody was: {}", res.text().await.unwrap_or_else(|e| e.to_string()));
        }
        let xml = res
            .text()
            .await?;
        let Some(mpi) = extract_mpi(&xml) else {
            ttp_bail!("Failed to get mpi from response: {xml}");
        };
        let psn = self.request_pseudonym(&mpi).await?;
        let patient = Patient::builder()
            .identifier(vec![Some(Identifier::builder()
                .system(self.project_id_system.clone())
                .value(psn)
                .build()
                .unwrap(),
        )])
            .build()
            .unwrap();
        Ok(patient)
    }

    async fn request_pseudonym(&self, ident: &str) -> Result<String, TtpError> {
        let url = self
            .url
            .join("gpas/gpasService")
            .unwrap();
        let Self { gpas_domain, .. } = self;
        let xml_body = format!(r#"<?xml version="1.0" encoding="utf-8"?>
            <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:tns="http://psn.ttp.ganimed.icmvc.emau.org/">
            <soapenv:Header/>
            <soapenv:Body>
                <tns:getOrCreatePseudonymFor>
                    <!-- The parameters for the operation -->
                    <value>{ident}</value>
                    <domainName>{gpas_domain}</domainName>
                </tns:getOrCreatePseudonymFor>
            </soapenv:Body>
            </soapenv:Envelope>
        "#);
        let res = self
            .client
            .post(url)
            .body(xml_body)
            .add_auth(&self.ttp_auth)
            .await?
            .send()
            .await?;
        if let Err(e) = res.error_for_status_ref() {
            ttp_bail!("Error requesting pseudonym: {e:#}\nBody was: {}", res.text().await.unwrap_or_else(|e| e.to_string()));
        }
        let xml_res = res.text().await?;
        let Some((_, psn_start)) = xml_res.split_once("<psn>") else {
            ttp_bail!("Response did not contain a <psn> tag: {xml_res}");
        };
        let psn = psn_start.chars().take_while(|c| *c != '<').collect::<String>();
        Ok(psn)
    }
}

fn extract_mpi(xml: &str) -> Option<&str> {
    // 1. Find the start of the <mpiId> block and get everything after it.
    xml.split_once("<mpiId>")?.1
        // 2. From that remainder, find the end of the block and get everything before it.
        .split_once("</mpiId>")?.0
        // 3. In the isolated block, find the <value> tag and get everything after it.
        .split_once("<value>")?.1
        // 4. Finally, find the closing </value> tag and get the content before it.
        .split_once("</value>")
        .map(|(value, _)| value.trim())
}

fn patient_to_xml(patient: &Patient) -> impl Display {
    struct PatientXml<'a> {
        patient: &'a Patient,
    }
    impl Display for PatientXml<'_> {
        fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
            let patient = self.patient;
            if let Some(name) = patient.name.first().and_then(Option::as_ref) {
                if let Some(given) = name.given.first().and_then(Option::as_ref) {
                    write!(f, "<firstName>{given}</firstName>")?;
                }
                if let Some(family) = &name.family {
                    write!(f, "<lastName>{family}</lastName>")?;
                }
            }
            if let Some(gender) = patient.gender {
                let gender = match gender {
                    AdministrativeGender::Female => 'F',
                    AdministrativeGender::Male => 'M',
                    AdministrativeGender::Other => 'O',
                    AdministrativeGender::Unknown => 'U',
                };
                write!(f, "<gender>{gender}</gender>")?;
            }
            if let Some(birth_date) = patient.birth_date.as_ref() {
                match birth_date {
                    fhir_sdk::Date::Date(dt) => {
                        write!(
                            f,
                            "<birthDate>{}-{:02}-{:02}T00:00:00</birthDate>",
                            dt.year(),
                            dt.month() as u8,
                            dt.day()
                        )?;
                    }
                    fhir_sdk::Date::YearMonth(year, month) => {
                        write!(
                            f,
                            "<birthDate>{year}-{:02}-01T00:00:00</birthDate>",
                            *month as u8
                        )?;
                    }
                    fhir_sdk::Date::Year(year) => {
                        write!(f, "<birthDate>{year}-01-01T00:00:00</birthDate>")?;
                    }
                }
            }
            if let Some(address) = patient.address.first().and_then(Option::as_ref) {
                f.write_str("<contacts>")?;
                if let Some(line) = address.line.first().and_then(Option::as_ref) {
                    write!(f, "<street>{line}</street>")?;
                }
                if let Some(city) = &address.city {
                    write!(f, "<city>{city}</city>")?;
                }
                if let Some(postal_code) = &address.postal_code {
                    write!(f, "<zipCode>{postal_code}</zipCode>")?;
                }
                f.write_str("</contacts>")?;
            }
            Ok(())
        }
    }
    PatientXml { patient }
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
            epix_domain: "Demo".into(),
            gpas_domain: "MII".into(),
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
            epix_domain: "Demo".into(),
            gpas_domain: "Transferstelle A".into(),
        };
        dbg!(ttp.request_project_pseudonym(fake_patient())
            .await
            .unwrap());
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
