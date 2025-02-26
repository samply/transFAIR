mod mainzelliste;
mod greifswald;

use axum::response::IntoResponse;
use clap::{Args, ValueEnum};
use fhir_sdk::r4b::resources::{Consent, Patient};
use reqwest::{Client, StatusCode, Url};
use serde::{Deserialize, Serialize};
use thiserror::Error;

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

    #[arg(skip)]
    client: Client,
}

#[derive(ValueEnum, Clone, Copy, Debug, Serialize, Deserialize)]
pub enum TtpType {
    Mainzelliste,
    Greifswald
}

impl Ttp {
    pub async fn check_availability(&self) -> bool {
        match self.ttp_type {
            TtpType::Mainzelliste => mainzelliste::check_availability(self).await,
            TtpType::Greifswald => greifswald::check_availability(self).await,
        }
    }

    pub async fn check_idtype_available(&self, idtype: &str) -> bool {
        match self.ttp_type {
            TtpType::Mainzelliste => mainzelliste::check_idtype_available(self, idtype).await,
            TtpType::Greifswald => greifswald::check_idtype_available(self, idtype).await,
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
            TtpType::Greifswald => Err((StatusCode::NOT_IMPLEMENTED, "Documenting patient consent with Greifswald tools is not yet implemented")),
        }
    }

    pub async fn request_project_pseudonym(
        &self,
        patient: &Patient,
    ) -> axum::response::Result<Patient> {
        Ok(match self.ttp_type {
            TtpType::Mainzelliste => mainzelliste::request_project_pseudonym(patient, self).await?,
            TtpType::Greifswald => greifswald::request_project_pseudonym(self, patient).await?,
        })
    }
}


#[derive(Debug, Error)]
enum TtpError {
    #[error("Failed to request Ttp: {0:#}")]
    RequestError(#[from] reqwest::Error),
    #[error(transparent)]
    Other(#[from] anyhow::Error),
}

impl IntoResponse for TtpError {
    fn into_response(self) -> axum::response::Response {
        tracing::warn!("{self:#}");
        match self {
            TtpError::RequestError(..) => {
                (StatusCode::SERVICE_UNAVAILABLE, "Failed to connect to ttp").into_response()
            },
            TtpError::Other(..) => StatusCode::INTERNAL_SERVER_ERROR.into_response(),
        }
    }
}

#[macro_export]
macro_rules! ttp_bail {
    ($tokens:tt) => {
        return Err(TtpError::Other(anyhow::anyhow!($tokens)))
    };
}