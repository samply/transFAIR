use clap::{Parser, Args};
use reqwest::Url;

#[derive(Parser, Clone, Debug)]
#[clap(author, version, about, long_about = None)]
pub struct Config {
    #[clap(flatten)]
    pub ttp: Option<Ttp>,
    #[clap(long, env, default_value = "TOKEN")]
    pub token_system: String,
    #[clap(long, env)]
    pub database_url: Url,
    #[clap(long, env)]
    pub consent_fhir_url: String,
    #[clap(long, env)]
    pub consent_fhir_api_key: String,
    #[clap(long, env)]
    pub mdat_fhir_url: String,
    #[clap(long, env)]
    pub mdat_fhir_api_key: String,
    #[clap(long, env)]
    pub project_fhir_url: String,
    #[clap(long, env)]
    pub project_fhir_api_key: String,
}

#[derive(Args, Clone, Debug)]
#[group(requires = "url", requires = "api_key")]
pub struct Ttp {
    #[arg(required = false, long = "ttp-url", env = "TTP_URL")]
    pub url: Url,
    #[arg(required = false, long = "ttp-api-key", env = "TTP_API_KEY")]
    pub api_key: String
}
