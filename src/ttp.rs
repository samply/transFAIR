pub(crate) mod mainzelliste;
pub mod greifswald;

use std::ops::Deref;

use axum::response::IntoResponse;
use fhir_sdk::r4b::resources::{Consent, Patient};
use reqwest::{StatusCode, Url};
use thiserror::Error;

use crate::config::Auth;

#[derive(clap::Args, Debug, Clone)]
pub struct TtpInner {
    #[clap(
        long = "ttp-url",
        env = "TTP_URL"
    )]
    pub url: Url,

    // defines the identifier to safe in the project database
    #[clap(long, env)]
    pub project_id_system: String,

    #[clap(
        long = "ttp-auth",
        env = "TTP_AUTH",
        default_value = ""
    )]
    pub ttp_auth: Auth,
}

#[derive(Debug, Clone, clap::Subcommand)]
pub enum Ttp {
    Mainzelliste(mainzelliste::MlConfig),
    Greifswald(greifswald::GreifswaldConfig)
}

impl Deref for Ttp {
    type Target = TtpInner;

    fn deref(&self) -> &Self::Target {
        match self {
            Ttp::Mainzelliste(config) => config,
            Ttp::Greifswald(config) => config,
        }
    }
}

impl Ttp {
    pub async fn check_availability(&self) -> bool {
        match self {
            Ttp::Mainzelliste(config) => config.check_availability().await,
            Ttp::Greifswald(config) => config.check_availability().await,
        }
    }

    pub async fn check_idtype_available(&self, idtype: &str) -> bool {
        match self {
            Ttp::Mainzelliste(config) => config.check_idtype_available(idtype).await,
            Ttp::Greifswald(config) => config.check_idtype_available(idtype).await,
        }
    }

    pub async fn document_patient_consent(
        &self,
        consent: &Consent,
        patient: &Patient,
    ) -> Result<(), (StatusCode, &'static str)> {
        match self {
            Ttp::Mainzelliste(config) => config.document_patient_consent(consent, patient).await,
            Ttp::Greifswald(..) => Err((StatusCode::NOT_IMPLEMENTED, "Documenting patient consent with Greifswald tools is not yet implemented")),
        }
    }

    pub async fn request_project_pseudonym(
        &self,
        patient: Patient,
        exchange_id_system: &str,
    ) -> axum::response::Result<Patient> {
        match self {
            Ttp::Mainzelliste(config) => config.request_project_pseudonym(patient, exchange_id_system).await.map_err(Into::into),
            Ttp::Greifswald(config) => config.request_project_pseudonym(patient).await.map_err(Into::into),
        }
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
    ($($tokens:tt)*) => {
        return Err(TtpError::Other(anyhow::anyhow!($($tokens)*)))
    };
}
