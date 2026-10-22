use std::sync::{Arc, Mutex};
use sha2::{Digest, Sha256};

use rustls::client::danger::{HandshakeSignatureValid, ServerCertVerified, ServerCertVerifier};
use rustls::pki_types::{CertificateDer, PrivateKeyDer, PrivatePkcs8KeyDer, ServerName, UnixTime};
use rustls::{ClientConfig, DigitallySignedStruct, ServerConfig, SignatureScheme};

static IDENTITY_LOCK: Mutex<()> = Mutex::new(());
static IDENTITY: Mutex<Option<(Vec<u8>, Vec<u8>)>> = Mutex::new(None);
static SERVER_CONFIG: Mutex<Option<Arc<ServerConfig>>> = Mutex::new(None);

fn install_crypto_provider() {
    let _ = rustls::crypto::ring::default_provider().install_default();
}

fn lock<T>(mutex: &Mutex<T>) -> std::sync::MutexGuard<'_, T> {
    mutex.lock().unwrap_or_else(|poisoned| poisoned.into_inner())
}

fn identity_dir() -> std::path::PathBuf {
    if let Ok(appdata) = std::env::var("APPDATA") {
        std::path::PathBuf::from(appdata).join("GLOOM")
    } else {
        std::env::temp_dir().join("GLOOM")
    }
}

pub fn fingerprint_hex(cert_der: &[u8]) -> String {
    Sha256::digest(cert_der)
        .iter()
        .map(|b| format!("{:02x}", b))
        .collect()
}

pub fn normalize_fingerprint(raw: &str) -> Option<[u8; 32]> {
    let clean: String = raw
        .chars()
        .filter(|c| !c.is_whitespace() && *c != ':')
        .collect::<String>()
        .to_ascii_lowercase();
    if clean.len() != 64 || !clean.chars().all(|c| c.is_ascii_hexdigit()) {
        return None;
    }
    let mut out = [0u8; 32];
    for (i, chunk) in clean.as_bytes().chunks(2).enumerate() {
        out[i] = u8::from_str_radix(std::str::from_utf8(chunk).ok()?, 16).ok()?;
    }
    Some(out)
}

fn read_identity_files() -> Option<(Vec<u8>, Vec<u8>)> {
    let dir = identity_dir();
    let cert = std::fs::read(dir.join("bridge_cert.der")).ok()?;
    let key = std::fs::read(dir.join("bridge_key.der")).ok()?;
    if cert.is_empty() || key.is_empty() {
        return None;
    }
    Some((cert, key))
}

fn write_identity_files(cert: &[u8], key: &[u8]) -> Result<(), String> {
    let dir = identity_dir();
    std::fs::create_dir_all(&dir)
        .map_err(|e| format!("Cannot create bridge identity directory: {}", e))?;
    let cert_tmp = dir.join("bridge_cert.der.tmp");
    let key_tmp = dir.join("bridge_key.der.tmp");
    std::fs::write(&cert_tmp, cert).map_err(|e| format!("Cannot persist bridge certificate: {}", e))?;
    std::fs::write(&key_tmp, key).map_err(|e| format!("Cannot persist bridge key: {}", e))?;
    std::fs::rename(&cert_tmp, dir.join("bridge_cert.der"))
        .map_err(|e| format!("Cannot install bridge certificate: {}", e))?;
    std::fs::rename(&key_tmp, dir.join("bridge_key.der"))
        .map_err(|e| format!("Cannot install bridge key: {}", e))?;
    Ok(())
}

fn generate_identity() -> Result<(Vec<u8>, Vec<u8>), String> {
    let key_pair = rcgen::KeyPair::generate()
        .map_err(|e| format!("Cannot generate bridge TLS key: {}", e))?;
    let params = rcgen::CertificateParams::new(vec!["gloom-bridge".to_string(), "jarvis-bridge".to_string()])
        .map_err(|e| format!("Cannot build bridge certificate parameters: {}", e))?;
    let cert = params
        .self_signed(&key_pair)
        .map_err(|e| format!("Cannot self-sign bridge certificate: {}", e))?;
    Ok((cert.der().to_vec(), key_pair.serialize_der()))
}

fn reset_identity() {
    let _guard = lock(&IDENTITY_LOCK);
    let dir = identity_dir();
    let _ = std::fs::remove_file(dir.join("bridge_cert.der"));
    let _ = std::fs::remove_file(dir.join("bridge_key.der"));
    let _ = std::fs::remove_file(dir.join("bridge_cert.der.tmp"));
    let _ = std::fs::remove_file(dir.join("bridge_key.der.tmp"));
    *lock(&IDENTITY) = None;
    *lock(&SERVER_CONFIG) = None;
}

fn ensure_identity() -> Result<(Vec<u8>, Vec<u8>), String> {
    if let Some(identity) = lock(&IDENTITY).clone() {
        return Ok(identity);
    }
    let _guard = lock(&IDENTITY_LOCK);
    if let Some(identity) = lock(&IDENTITY).clone() {
        return Ok(identity);
    }
    let identity = match read_identity_files() {
        Some(identity) => identity,
        None => {
            let identity = generate_identity()?;
            write_identity_files(&identity.0, &identity.1)?;
            identity
        }
    };
    *lock(&IDENTITY) = Some(identity.clone());
    Ok(identity)
}

pub fn local_fingerprint() -> String {
    ensure_identity()
        .map(|(cert, _)| fingerprint_hex(&cert))
        .unwrap_or_default()
}

fn build_server_config(cert_der: Vec<u8>, key_der: Vec<u8>) -> Result<Arc<ServerConfig>, String> {
    ServerConfig::builder()
        .with_no_client_auth()
        .with_single_cert(
            vec![CertificateDer::from(cert_der)],
            PrivateKeyDer::Pkcs8(PrivatePkcs8KeyDer::from(key_der)),
        )
        .map(Arc::new)
        .map_err(|e| format!("Cannot build bridge TLS server config: {}", e))
}

pub fn server_config() -> Result<Arc<ServerConfig>, String> {
    if let Some(config) = lock(&SERVER_CONFIG).clone() {
        return Ok(config);
    }
    install_crypto_provider();
    let (cert, key) = ensure_identity()?;
    let config = match build_server_config(cert, key) {
        Ok(config) => config,
        Err(first_error) => {
            eprintln!("Bridge identity is unusable ({}); regenerating", first_error);
            reset_identity();
            let (cert, key) = ensure_identity()?;
            build_server_config(cert, key)?
        }
    };
    *lock(&SERVER_CONFIG) = Some(config.clone());
    Ok(config)
}

pub fn issue_client_identity() -> Result<(String, String, String), String> {
    install_crypto_provider();
    let key_pair = rcgen::KeyPair::generate()
        .map_err(|e| format!("Cannot generate companion TLS key: {}", e))?;
    let params = rcgen::CertificateParams::new(vec!["gloom-android-bridge".to_string(), "jarvis-android-bridge".to_string()])
        .map_err(|e| format!("Cannot build companion certificate parameters: {}", e))?;
    let cert = params
        .self_signed(&key_pair)
        .map_err(|e| format!("Cannot self-sign companion certificate: {}", e))?;

    Ok((
        cert.pem(),
        key_pair.serialize_pem(),
        fingerprint_hex(cert.der()),
    ))
}

#[derive(Debug)]
struct PinnedVerifier {
    expected: [u8; 32],
}

impl ServerCertVerifier for PinnedVerifier {
    fn verify_server_cert(
        &self,
        end_entity: &CertificateDer<'_>,
        _intermediates: &[CertificateDer<'_>],
        _server_name: &ServerName<'_>,
        _ocsp_response: &[u8],
        _now: UnixTime,
    ) -> Result<ServerCertVerified, rustls::Error> {
        let actual: [u8; 32] = Sha256::digest(end_entity.as_ref()).into();
        if actual == self.expected {
            Ok(ServerCertVerified::assertion())
        } else {
            Err(rustls::Error::General(
                "pinned certificate mismatch".to_string(),
            ))
        }
    }

    fn verify_tls12_signature(
        &self,
        message: &[u8],
        cert: &CertificateDer<'_>,
        dss: &DigitallySignedStruct,
    ) -> Result<HandshakeSignatureValid, rustls::Error> {
        rustls::crypto::verify_tls12_signature(
            message,
            cert,
            dss,
            &rustls::crypto::ring::default_provider().signature_verification_algorithms,
        )
    }

    fn verify_tls13_signature(
        &self,
        message: &[u8],
        cert: &CertificateDer<'_>,
        dss: &DigitallySignedStruct,
    ) -> Result<HandshakeSignatureValid, rustls::Error> {
        rustls::crypto::verify_tls13_signature(
            message,
            cert,
            dss,
            &rustls::crypto::ring::default_provider().signature_verification_algorithms,
        )
    }

    fn supported_verify_schemes(&self) -> Vec<SignatureScheme> {
        rustls::crypto::ring::default_provider()
            .signature_verification_algorithms
            .supported_schemes()
    }
}

#[derive(Debug)]
struct PermissiveVerifier;

impl ServerCertVerifier for PermissiveVerifier {
    fn verify_server_cert(
        &self,
        _end_entity: &CertificateDer<'_>,
        _intermediates: &[CertificateDer<'_>],
        _server_name: &ServerName<'_>,
        _ocsp_response: &[u8],
        _now: UnixTime,
    ) -> Result<ServerCertVerified, rustls::Error> {
        Ok(ServerCertVerified::assertion())
    }

    fn verify_tls12_signature(
        &self,
        message: &[u8],
        cert: &CertificateDer<'_>,
        dss: &DigitallySignedStruct,
    ) -> Result<HandshakeSignatureValid, rustls::Error> {
        rustls::crypto::verify_tls12_signature(
            message,
            cert,
            dss,
            &rustls::crypto::ring::default_provider().signature_verification_algorithms,
        )
    }

    fn verify_tls13_signature(
        &self,
        message: &[u8],
        cert: &CertificateDer<'_>,
        dss: &DigitallySignedStruct,
    ) -> Result<HandshakeSignatureValid, rustls::Error> {
        rustls::crypto::verify_tls13_signature(
            message,
            cert,
            dss,
            &rustls::crypto::ring::default_provider().signature_verification_algorithms,
        )
    }

    fn supported_verify_schemes(&self) -> Vec<SignatureScheme> {
        rustls::crypto::ring::default_provider()
            .signature_verification_algorithms
            .supported_schemes()
    }
}

pub fn client_config(fingerprint: Option<&str>) -> Result<Arc<ClientConfig>, String> {
    install_crypto_provider();
    if let Some(fp) = fingerprint {
        if let Some(expected) = normalize_fingerprint(fp) {
            return Ok(Arc::new(
                ClientConfig::builder()
                    .dangerous()
                    .with_custom_certificate_verifier(Arc::new(PinnedVerifier { expected }))
                    .with_no_client_auth(),
            ));
        }
    }
    // Fallback permissive client config for LAN communication
    Ok(Arc::new(
        ClientConfig::builder()
            .dangerous()
            .with_custom_certificate_verifier(Arc::new(PermissiveVerifier))
            .with_no_client_auth(),
    ))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_fingerprint_normalization() {
        let raw = "AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99:Aa:Bb:Cc:Dd:Ee:Ff:00:11:22:33:44:55:66:77:88:99";
        let parsed = normalize_fingerprint(raw).expect("valid fingerprint");
        assert_eq!(parsed[0], 0xaa);
        assert_eq!(parsed[31], 0x99);
    }

    #[test]
    fn test_fingerprint_normalization_rejects_malformed() {
        assert!(normalize_fingerprint("short").is_none());
        assert!(normalize_fingerprint(&"g".repeat(64)).is_none());
    }

    #[test]
    fn test_fingerprint_hex_calculation() {
        assert_eq!(fingerprint_hex(b"test").len(), 64);
    }
}

