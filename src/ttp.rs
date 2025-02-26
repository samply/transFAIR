mod mainzelliste;
mod greifswald;

use std::ops::Deref;

use axum::response::IntoResponse;
use clap::{FromArgMatches, Parser, ValueEnum};
use fhir_sdk::r4b::resources::{Consent, Patient};
use reqwest::{Client, StatusCode, Url};
use thiserror::Error;

use crate::config::Auth;

#[derive(Parser, Debug, Clone)]
pub struct TtpInner {
    #[clap(
        long = "institute-ttp-url",
        env = "INSTITUTE_TTP_URL"
    )]
    pub url: Url,

    // defines the identifier to safe in the project database
    #[clap(long, env)]
    pub project_id_system: String,

    #[clap(long, env, default_value = "")]
    pub ttp_auth: Auth,

    #[clap(skip)]
    client: Client,
}

#[derive(Debug, Clone)]
pub enum Ttp {
    Mainzelliste(mainzelliste::MlConfig),
    Greifswald(greifswald::GreifswaldConfig)
}

#[derive(ValueEnum, Clone, Copy)]
enum TtpType {
    Mainzelliste,
    Greifswald,
}

#[derive(clap::Args)]
struct TtpTypeParser {
    #[clap(long, env, default_value = "mainzelliste")]
    ttp_type: TtpType,
}

impl FromArgMatches for Ttp {
    fn from_arg_matches(matches: &clap::ArgMatches) -> Result<Self, clap::Error> {
        let ttp = match TtpTypeParser::from_arg_matches(matches)?.ttp_type {
            TtpType::Mainzelliste => Ttp::Mainzelliste(mainzelliste::MlConfig::from_arg_matches(matches)?),
            TtpType::Greifswald => Ttp::Greifswald(greifswald::GreifswaldConfig::from_arg_matches(matches)?),
        };
        Ok(ttp)
    }

    fn update_from_arg_matches(&mut self, _matches: &clap::ArgMatches) -> Result<(), clap::Error> {
        Ok(())
    }
}

impl clap::Args for Ttp {
    fn augment_args(cmd: clap::Command) -> clap::Command {
        let cmd = TtpTypeParser::augment_args(cmd);
        cmd.defer(|cmd| {
            match TtpTypeParser::from_arg_matches(&cmd.clone().get_matches()).unwrap().ttp_type {
                TtpType::Mainzelliste => mainzelliste::MlConfig::augment_args(cmd),
                TtpType::Greifswald => greifswald::GreifswaldConfig::augment_args(cmd),
            }
        })
    }

    fn augment_args_for_update(cmd: clap::Command) -> clap::Command {
        cmd
    }
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
        consent: Consent,
        patient: &Patient,
    ) -> Result<Consent, (StatusCode, &'static str)> {
        match self {
            Ttp::Mainzelliste(config) => config.document_patient_consent(consent, patient).await,
            Ttp::Greifswald(..) => Err((StatusCode::NOT_IMPLEMENTED, "Documenting patient consent with Greifswald tools is not yet implemented")),
        }
    }

    pub async fn request_project_pseudonym(
        &self,
        patient: &Patient,
    ) -> axum::response::Result<Patient> {
        match self {
            Ttp::Mainzelliste(config) => config.request_project_pseudonym(patient).await.map_err(Into::into),
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
    ($tokens:tt) => {
        return Err(TtpError::Other(anyhow::anyhow!($tokens)))
    };
}