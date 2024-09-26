use clap::{Parser, Args};
use reqwest::Url;

#[derive(Parser, Clone, Debug)]
#[clap(author, version, about, long_about = None)]
pub struct Config {
    // Definition of necessary parameters for communicating with a ttp
    #[clap(flatten)]
    pub ttp: Option<Ttp>,
    // Either an id well-known to both, project and dic, or a temporary identifier created by the ttp
    #[clap(long, env, default_value = "TOKEN")]
    pub exchange_id_system: String,
    // Definition of the URL to use for the database, this is set Environment (see flake.nix) as sqlx expects this env variable
    #[clap(long, env)]
    pub database_url: Url,
    // Definition of the fhir server and credentials used for communicating data requests to the dic
    #[clap(long, env)]
    pub fhir_request_url: String,
    #[clap(long, env)]
    pub fhir_request_credentials: String,
    // Definition of the fhir server and credentials used for reading data from the dic
    #[clap(long, env)]
    pub fhir_input_url: String,
    #[clap(long, env)]
    pub fhir_input_credentials: String,
    // Definition of the fhir server and credentials used for adding data to the project data
    #[clap(long, env)]
    pub fhir_output_url: String,
    #[clap(long, env)]
    pub fhir_output_credentials: String,
}

#[derive(Args, Clone, Debug)]
#[group(requires = "url", requires = "api_key", requires = "project_id_system")]
pub struct Ttp {
    #[arg(required = false, long = "institute-ttp-url", env = "INSTITUTE_TTP_URL")]
    pub url: Url,
    #[arg(required = false, long = "institute-ttp-api-key", env = "INSTITUTE_TTP_API_KEY")]
    pub api_key: String,
    // defines the identifier to safe in the project database
    #[arg(required = false, long, env)]
    pub project_id_system: String,
}
