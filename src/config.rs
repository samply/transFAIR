use std::str::FromStr;

use clap::Parser;
use reqwest::Url;

use crate::ttp::Ttp;

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
    pub fhir_request_url: Url,
    #[clap(long, env)]
    pub fhir_request_credentials: Option<Auth>,
    // Definition of the fhir server and credentials used for reading data from the dic
    #[clap(long, env)]
    pub fhir_input_url: Url,
    #[clap(long, env)]
    pub fhir_input_credentials: Option<Auth>,
    // Definition of the fhir server and credentials used for adding data to the project data
    #[clap(long, env)]
    pub fhir_output_url: Url,
    #[clap(long, env)]
    pub fhir_output_credentials: Option<Auth>,
}

#[derive(Debug, Clone)]
pub enum Auth {
    Basic {
        user: String,
        pw: String,
    },
}

impl FromStr for Auth {
    type Err = &'static str;

    fn from_str(s: &str) -> Result<Self, Self::Err> {
        let (user, pw) = s.split_once(":").ok_or("Credentials should be in the form of '<user>:<pw>'")?;
        Ok(Self::Basic { user: user.to_owned(), pw: pw.to_owned() })
    }
}