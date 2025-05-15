{
  description = "transfair";
  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";
    rust-overlay = {
      url = "github:oxalica/rust-overlay";
      inputs = {
        nixpkgs.follows = "nixpkgs";
        flake-utils.follows = "flake-utils";
      };
    };
  };
  outputs = { self, nixpkgs, flake-utils, rust-overlay }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        name = "transfair";
        overlays = [ (import rust-overlay) ];
        pkgs = import nixpkgs { inherit system overlays; };
        rustToolchain = pkgs.pkgsBuildHost.rust-bin.fromRustupToolchainFile
          ./rust-toolchain.toml;
        nativeBuildInputs = with pkgs; [
          rustToolchain
          pkg-config
          lldb
          cargo-watch
        ];
        buildInputs = with pkgs; [ openssl sqlite sqlx-cli ];
      in rec {
        # nix develop
        devShells.default = pkgs.mkShell {
          inherit buildInputs nativeBuildInputs;
          # Environment for Routine Connector
          # TTP_URL = "http://localhost:8082";
          TTP_URL = "http://test.local:8082";
          TTP_ML_API_KEY = "routine-connector-password";
          FHIR_REQUEST_URL = "http://localhost:8085";
          # FHIR_REQUEST_CREDENTIALS = "bla:test";
          FHIR_INPUT_URL = "http://localhost:8086";
          # FHIR_INPUT_CREDENTIALS = "foo:test";
          FHIR_OUTPUT_URL = "http://localhost:8095";
          # FHIR_OUTPUT_CREDENTIALS = "foobar:test";
          EXCHANGE_ID_SYSTEM = "SESSION_ID";
          PROJECT_ID_SYSTEM = "PROJECT_1_ID";
          DATABASE_URL = "sqlite://data_requests.sql?mode=rwc";
          RUST_LOG = "trace";
          no_proxy = "localhost,test.local";
          # Environment for Mainzelliste
          ML_DB_PASS = "my-secret-db-password";
          ML_ROUTINE_CONNECTOR_PASSPHRASE = "routine-connector-password";
          ML_DIZ_PASSPHRASE = "diz-password";
          ML_LOG_LEVEL = "debug";
          TLS_CA_CERTIFICATES_DIR = "./test-certs";
          # Start Compose Environment when opening Project
          shellHook = ''
            docker compose up -d
          '';
        };
      });
}
