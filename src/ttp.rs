mod mainzelliste;

use clap::{Args, ValueEnum};
use fhir_sdk::r4b::resources::{Consent, Patient};
use reqwest::{StatusCode, Url};
use serde::{Deserialize, Serialize};

#[derive(Args, Clone, Debug)]
#[group(requires = "url", requires = "api_key", requires = "project_id_system")]
pub struct Ttp {
    #[arg(
        required = false,
        long = "institute-ttp-url",
        env = "INSTITUTE_TTP_URL"
    )]
    pub url: Url,
    #[arg(
        required = false,
        long = "institute-ttp-api-key",
        env = "INSTITUTE_TTP_API_KEY"
    )]
    pub api_key: String,

    // defines the identifier to safe in the project database
    #[arg(required = false, long, env)]
    pub project_id_system: String,

    #[arg(required = false, long, env, default_value = "mainzelliste")]
    pub ttp_type: TtpType,
}

#[derive(ValueEnum, Clone, Copy, Debug, Serialize, Deserialize)]
pub enum TtpType {
    Mainzelliste,
}

impl Ttp {
    pub async fn check_availability(&self) -> bool {
        match self.ttp_type {
            TtpType::Mainzelliste => mainzelliste::check_availability(self).await,
        }
    }

    pub async fn check_idtype_available(&self, idtype: &str) -> bool {
        match self.ttp_type {
            TtpType::Mainzelliste => mainzelliste::check_idtype_available(self, idtype).await,
        }
    }

    pub async fn document_patient_consent(
        &self,
        consent: Consent,
        patient: &Patient,
    ) -> Result<Consent, (StatusCode, &'static str)> {
        match self.ttp_type {
            TtpType::Mainzelliste => {
                mainzelliste::document_patient_consent(consent, patient, self).await
            }
        }
    }

    pub async fn request_project_pseudonym(
        &self,
        patient: &mut Patient,
    ) -> Result<Patient, (StatusCode, &'static str)> {
        match self.ttp_type {
            TtpType::Mainzelliste => mainzelliste::request_project_pseudonym(patient, self).await,
        }
    }
}
