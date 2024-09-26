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
    pub fhir_request_url: String,
    #[clap(long, env)]
    pub fhir_request_credentials: String,
    #[clap(long, env)]
    pub fhir_input_url: String,
    #[clap(long, env)]
    pub fhir_input_credentials: String,
    #[clap(long, env)]
    pub fhir_output_url: String,
    #[clap(long, env)]
    pub fhir_output_credentials: String,
}

#[derive(Args, Clone, Debug)]
#[group(requires = "url", requires = "api_key")]
pub struct Ttp {
    #[arg(required = false, long = "institute-ttp-url", env = "INSTITUTE_TTP_URL")]
    pub url: Url,
    #[arg(required = false, long = "institute-ttp-api-key", env = "INSTITUTE_TTP_API_KEY")]
    pub api_key: String
}
