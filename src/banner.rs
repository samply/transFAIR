use tracing::info;

pub(crate) fn print_banner() {
    info!(
        ":newspaper: Routine Connector v{} (built {} {}) starting up ...",
        env!("CARGO_PKG_VERSION"),
        env!("BUILD_DATE"),
        env!("BUILD_TIME"),
    );
}
