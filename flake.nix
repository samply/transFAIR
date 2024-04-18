{
  description = "wip-routine-connector";
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
  outputs = { self, nixpkgs, flake-utils, rust-overlay}:
    flake-utils.lib.eachDefaultSystem
      (system:
        let
          name = "wip-routine-connector";
          overlays = [ (import rust-overlay) ];
          pkgs = import nixpkgs {
            inherit system overlays;
          };
          rustToolchain = pkgs.pkgsBuildHost.rust-bin.fromRustupToolchainFile ./rust-toolchain.toml;
          nativeBuildInputs = with pkgs; [ rustToolchain pkg-config ];
          buildInputs = with pkgs; [ openssl ];
        in
        rec {
          # nix develop
          devShells.default = pkgs.mkShell {
            inherit buildInputs nativeBuildInputs;
            # Environment for Routine Connector
            INSTITUTE_TTP_URL="http://localhost:8081";
            INSTITUTE_TTP_API_KEY="routine-connector-password";
            PROJECTS=''
            {
              "consent_fhir_url": "http://localhost:8085",
              "consent_fhir_api_key": "bla",
              "mdat_fhir_url": "http://localhost:8086",
              "mdat_fhir_api_key": "foo",
              "project_fhir_url": "http://localhost:8095",
              "project_fhir_api_key": "foobar"
            }
            '';
            RUST_LOG="trace";
            no_proxy="localhost";
            # Environment for Mainzelliste
            ML_DB_PASS="my-secret-db-password";
            ML_ROUTINE_CONNECTOR_PASSPHRASE="routine-connector-password";
            ML_DIZ_PASSPHRASE="diz-password";
            ML_LOG_LEVEL="debug";
            # Start Compose Environment when opening Project
            shellHook = ''
              docker compose up -d
            '';
          };
        }
      );
}
