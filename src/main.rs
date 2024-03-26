use tracing::Level;
use tracing_subscriber::{EnvFilter, util::SubscriberInitExt};

mod banner;

fn main() {
    tracing_subscriber::FmtSubscriber::builder()
        .with_max_level(Level::DEBUG)
        .with_env_filter(EnvFilter::from_default_env())
        .finish()
        .init();
    banner::print_banner();
}
