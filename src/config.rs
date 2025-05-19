use std::{collections::HashMap, fs, path::PathBuf, str::FromStr, sync::LazyLock, time::{Duration, Instant}};

use clap::{Args, CommandFactory, FromArgMatches, Parser};
use reqwest::{Certificate, Client, Url};
use anyhow::anyhow;
use tokio::sync::RwLock;
use tracing::info;

use crate::{ttp::{self, greifswald::GreifswaldConfig, mainzelliste::MlConfig, Ttp}, CONFIG};

#[derive(Parser, Clone, Debug)]
#[clap(author, version, about, long_about = None)]
pub struct Config {
    // Definition of necessary parameters for communicating with a ttp
    #[clap(skip)]
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
    #[clap(long, env, default_value = "")]
    pub fhir_request_credentials: Auth,
    // Definition of the fhir server and credentials used for reading data from the dic
    #[clap(long, env)]
    pub fhir_input_url: Url,
    #[clap(long, env, default_value = "")]
    pub fhir_input_credentials: Auth,
    // Definition of the fhir server and credentials used for adding data to the project data
    #[clap(long, env)]
    pub fhir_output_url: Url,
    #[clap(long, env, default_value = "")]
    pub fhir_output_credentials: Auth,
    /// Trusted tls root certificates
    #[clap(long, env)]
    pub tls_ca_certificates_dir: Option<PathBuf>,
    /// Disable TLS verification
    #[clap(long, env, default_value_t = false)]
    pub tls_disable: bool,

    #[clap(skip)]
    pub client: Client,
}

impl Config {
    pub fn parse() -> Self {
        let cmd = Config::command();
        let ttp_cmd = ttp::Ttp::augment_args(cmd.clone());
        let args_matches = cmd.get_matches();
        let mut this = Self::from_arg_matches(&args_matches).map_err(|e| e.exit()).unwrap();
        let ca_client = build_client(&this.tls_ca_certificates_dir, this.tls_disable);
        this.client = ca_client.clone();
        let mut ttp = ttp_cmd.try_get_matches().ok().and_then(|matches| Ttp::from_arg_matches(&matches).ok());
        if let Some(ref mut ttp) = ttp {
            let (Ttp::Mainzelliste(MlConfig {base, ..}) | Ttp::Greifswald(GreifswaldConfig {base, ..})) = ttp;
            base.client = ca_client.clone();
        }
        this.ttp = ttp;
        this
    }
}

fn build_client(tls_ca_certificates_dir: &Option<PathBuf>, disable_tls: bool) -> Client {
    let mut client_builder = Client::builder();
    client_builder = client_builder
        .danger_accept_invalid_hostnames(disable_tls)
        .danger_accept_invalid_certs(disable_tls);
    if let Some(tls_ca_dir) = tls_ca_certificates_dir {
        info!("Loading available custom ca certificates from {:?}", tls_ca_certificates_dir);
        for path_res in tls_ca_dir.read_dir().expect(&format!("Unable to read {:?}", tls_ca_certificates_dir)) {
            if let Ok(path_buf) = path_res {
                info!("Adding custom ca certificate {:?}", path_buf.path());
                client_builder = client_builder.add_root_certificate(
                    Certificate::from_pem(
                        &fs::read(path_buf.path()).expect(&format!("Unable to read file provided: {:?}", path_buf.path()))
                    ).expect(&format!("Unable to convert {:?} to a certificate. Please verify it is a valid pem file", path_buf.path()))
                );
            }
        }
    }

    client_builder.build().expect("Unable to initially build reqwest client")
}

#[derive(Debug, Clone)]
pub enum Auth {
    None,
    Basic {
        user: String,
        pw: String,
    },
    Oauth {
        client_id: String,
        client_secret: String,
        token_url: Url,
    }
}

impl FromStr for Auth {
    type Err = anyhow::Error;

    fn from_str(s: &str) -> Result<Self, Self::Err> {
        if s.is_empty() {
            return Ok(Self::None);
        }
        if s.starts_with("OAuth") {
            let mut parts = s.split(' ').skip(1);
            return Ok(Self::Oauth {
                client_id: parts.next().ok_or(anyhow!("Missing client id"))?.into(),
                client_secret: parts.next().ok_or(anyhow!("Missing client secret"))?.into(),
                token_url: parts.next().ok_or(anyhow!("Missing OAuth token endpoint url"))?.parse()?,
            })
        }
        let (user, pw) = s.split_once(":").ok_or(anyhow!("Credentials should be in the form of '<user>:<pw>'"))?;
        Ok(Self::Basic { user: user.to_owned(), pw: pw.to_owned() })
    }
}

pub trait ClientBuilderExt {
    async fn add_auth(self, auth: &Auth) -> reqwest::Result<reqwest::RequestBuilder>;
}

static OIDC_TOKENS: LazyLock<RwLock<HashMap<String, (Instant, String)>>> = LazyLock::new(Default::default);

impl ClientBuilderExt for reqwest::RequestBuilder {
    async fn add_auth(self, auth: &Auth) -> reqwest::Result<Self> {
        let res = match auth {
            Auth::Basic { user, pw } => self.basic_auth(user, Some(pw)),
            Auth::Oauth { client_id, client_secret, token_url } => {
                {
                    let read_lock = OIDC_TOKENS.read().await;
                    if let Some((ttl, token)) = read_lock.get(client_id) {
                        if *ttl <= Instant::now() {
                            return Ok(self.bearer_auth(token));
                        }
                    }
                }
                #[derive(serde::Deserialize)]
                struct TokenRes {
                    expires_in: u64,
                    access_token: String,
                }
                let TokenRes { expires_in, access_token } = CONFIG.client
                    .post(token_url.clone())
                    .form(&serde_json::json!({
                        "grant_type": "client_credentials",
                        "client_id": client_id,
                        "client_secret": client_secret
                    }))
                    .send()
                    .await?
                    .error_for_status()?
                    .json::<TokenRes>()
                    .await?;
                let res = self.bearer_auth(&access_token);
                OIDC_TOKENS.write().await.insert(client_id.clone(), (Instant::now() + Duration::from_secs(expires_in), access_token));
                res
            }
            Auth::None => self,
        };
        Ok(res)
    }
}
