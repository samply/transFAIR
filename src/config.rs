use std::str::FromStr;

use clap::Parser;
use reqwest::Url;
use serde::Deserialize;

#[derive(Parser, Clone, Debug)]
#[clap(author, version, about, long_about = None)]
pub struct Config {
    #[clap(long, env)]
    pub institute_ttp_url: Url,
    #[clap(long, env)]
    pub institute_ttp_api_key: String,
    // NOTE: You can't pass multiple projects through environment, only through args like
    // cargo run -- --projects '<project_1>' '<project_2>'
    #[clap(long, env, num_args = 1.., value_delimiter=';', required = true)]
    pub projects: Vec<ProjectConfig>,
}

#[derive(Clone, Debug, Deserialize)]
pub struct ProjectConfig {
    pub consent_fhir_url: String,
    pub consent_fhir_api_key: String,
    pub mdat_fhir_url: String,
    pub mdat_fhir_api_key: String,
    pub project_fhir_url: String,
    pub project_fhir_api_key: String,
}

impl FromStr for ProjectConfig {
    type Err = String;

    fn from_str(s: &str) -> Result<Self, Self::Err> {
        let res: ProjectConfig =
            serde_json::from_str(s).map_err(|e| format!("error parsing project: {}", e))?;
        Ok(res)
    }
}
