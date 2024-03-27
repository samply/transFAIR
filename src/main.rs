use clap::Parser;
use config::Config;
use once_cell::sync::Lazy;
use tracing::{Level, debug};
use tracing_subscriber::{EnvFilter, util::SubscriberInitExt};

mod banner;
mod config;

static CONFIG: Lazy<Config> = Lazy::new(Config::parse);

fn main() {
    tracing_subscriber::FmtSubscriber::builder()
        .with_max_level(Level::DEBUG)
        .with_env_filter(EnvFilter::from_default_env())
        .finish()
        .init();
    banner::print_banner();
    debug!("{:#?}", Lazy::force(&CONFIG));
}
