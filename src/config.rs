use std::{collections::HashMap, fs, path::PathBuf, str::FromStr, sync::LazyLock, time::{Duration, Instant}};

use clap::{Args, CommandFactory, FromArgMatches, Parser};
use jsonwebtoken::EncodingKey;
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
        let cmd = ttp::Ttp::augment_args(cmd);
        let args_matches = cmd.get_matches();
        let mut this = Self::from_arg_matches(&args_matches).map_err(|e| e.exit()).unwrap();
        let ca_client = build_client(&this.tls_ca_certificates_dir, this.tls_disable);
        this.client = ca_client.clone();
        let mut ttp = Ttp::from_arg_matches(&args_matches).ok();
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

#[derive(Clone)]
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
    },
    Smart {
        client_id: String,
        priv_key: EncodingKey,
        token_url: Url,
        scope: String,
    }
}

impl std::fmt::Debug for Auth {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            Self::None => write!(f, "None"),
            Self::Basic { user, pw } => f.debug_struct("Basic").field("user", user).field("pw", pw).finish(),
            Self::Oauth { client_id, token_url, .. } => f.debug_struct("Oauth").field("client_id", client_id).field("token_url", token_url).finish(),
            Self::Smart { client_id, token_url, scope, .. } => f.debug_struct("Smart").field("client_id", client_id).field("token_url", token_url).field("scope", scope).finish(),
        }
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
        if s.starts_with("Smart") {
            let mut parts = s.split(' ').skip(1);
            return Ok(Self::Smart {
                client_id: parts.next().ok_or(anyhow!("Missing client id"))?.into(),
                priv_key: {
                    let path = parts.next().ok_or(anyhow!("Missing private key file"))?;
                    EncodingKey::from_rsa_pem(&fs::read(path)?)?
                },
                token_url: parts.next().ok_or(anyhow!("Missing OAuth token endpoint url"))?.parse()?,
                scope: parts.next().unwrap_or("system/*.rs").into()
            })
        }
        let (user, pw) = s.split_once(":").ok_or(anyhow!("Credentials should be in the form of '<user>:<pw>'"))?;
        Ok(Self::Basic { user: user.to_owned(), pw: pw.to_owned() })
    }
}

pub trait ClientBuilderExt {
    async fn add_auth(self, auth: &Auth) -> anyhow::Result<reqwest::RequestBuilder>;
}

static OIDC_TOKENS: LazyLock<RwLock<HashMap<String, (Instant, String)>>> = LazyLock::new(Default::default);

impl ClientBuilderExt for reqwest::RequestBuilder {
    async fn add_auth(self, auth: &Auth) -> anyhow::Result<Self> {
        #[derive(serde::Deserialize)]
        struct TokenRes {
            expires_in: u64,
            access_token: String,
        }
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
            Auth::Smart {
                client_id,
                priv_key,
                token_url,
                scope
            } => {
                {
                    let read_lock = OIDC_TOKENS.read().await;
                    if let Some((ttl, token)) = read_lock.get(client_id) {
                        if *ttl <= Instant::now() {
                            return Ok(self.bearer_auth(token));
                        }
                    }
                }
                let header = jsonwebtoken::Header::new(jsonwebtoken::Algorithm::RS384);
                let claims = serde_json::json!({
                    "iss": client_id,
                    "aud": token_url.as_str(),
                    "sub": client_id,
                    "jti": uuid::Uuid::new_v4().to_string(),
                    "exp": jsonwebtoken::get_current_timestamp() + 60,
                });
                let client_assertion = jsonwebtoken::encode(&header, &claims, &priv_key)?;
                let res = CONFIG.client
                    .post(token_url.clone())
                    .form(&serde_json::json!({
                        "grant_type": "client_credentials",
                        "scope": scope,
                        "client_assertion_type": "urn:ietf:params:oauth:client-assertion-type:jwt-bearer",
                        "client_assertion": client_assertion
                    }))
                    .send()
                    .await?;
                if let Err(e) = res.error_for_status_ref() {
                    return Err(anyhow!("Error while requesting token: {e}\n Response: {}", res.text().await?));
                }
                let TokenRes { expires_in, access_token } = res.json::<TokenRes>().await?;
                let res = self.bearer_auth(&access_token);
                OIDC_TOKENS.write().await.insert(client_id.clone(), (Instant::now() + Duration::from_secs(expires_in), access_token));
                res
            }
            Auth::None => self,
        };
        Ok(res)
    }
}
