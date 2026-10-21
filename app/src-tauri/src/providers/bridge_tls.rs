use std::sync::{Arc, OnceLock};

use sha2::{Digest, Sha256};

use rustls::client::danger::{HandshakeSignatureValid, ServerCertVerified, ServerCertVerifier};
use rustls::pki_types::{CertificateDer, PrivateKeyDer, PrivatePkcs8KeyDer, ServerName, UnixTime};
use rustls::{ClientConfig, DigitallySignedStruct, ServerConfig, SignatureScheme};

static SERVER_CONFIG: OnceLock<Result<Arc<ServerConfig>, String>> = OnceLock::new();
static LOCAL_FINGERPRINT: OnceLock<String> = OnceLock::new();

fn install_crypto_provider() {
    let _ = rustls::crypto::ring::default_provider().install_default();
}

fn identity_dir() -> std::path::PathBuf {
    crate::config::dirs_fallback().join("JARVIS")
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

fn load_or_create_server_identity() -> Result<(Vec<u8>, Vec<u8>), String> {
    let dir = identity_dir();
    std::fs::create_dir_all(&dir)
        .map_err(|e| format!("Cannot create bridge identity directory: {}", e))?;
    let cert_path = dir.join("bridge_cert.der");
    let key_path = dir.join("bridge_key.der");

    if cert_path.exists() && key_path.exists() {
        if let (Ok(cert), Ok(key)) = (std::fs::read(&cert_path), std::fs::read(&key_path)) {
            if !cert.is_empty() && !key.is_empty() {
                return Ok((cert, key));
            }
        }
    }

    let key_pair = rcgen::KeyPair::generate()
        .map_err(|e| format!("Cannot generate bridge TLS key: {}", e))?;
    let params = rcgen::CertificateParams::new(vec!["jarvis-bridge".to_string()])
        .map_err(|e| format!("Cannot build bridge certificate parameters: {}", e))?;
    let cert = params
        .self_signed(&key_pair)
        .map_err(|e| format!("Cannot self-sign bridge certificate: {}", e))?;

    let cert_der = cert.der().to_vec();
    let key_der = key_pair.serialize_der();
    std::fs::write(&cert_path, &cert_der)
        .map_err(|e| format!("Cannot persist bridge certificate: {}", e))?;
    std::fs::write(&key_path, &key_der)
        .map_err(|e| format!("Cannot persist bridge key: {}", e))?;
    Ok((cert_der, key_der))
}

pub fn local_fingerprint() -> String {
    LOCAL_FINGERPRINT
        .get_or_init(|| {
            load_or_create_server_identity()
                .map(|(cert, _)| fingerprint_hex(&cert))
                .unwrap_or_default()
        })
        .clone()
}

pub fn server_config() -> Result<Arc<ServerConfig>, String> {
    SERVER_CONFIG
        .get_or_init(|| {
            install_crypto_provider();
            let (cert_der, key_der) = load_or_create_server_identity()?;
            let config = ServerConfig::builder()
                .with_no_client_auth()
                .with_single_cert(
                    vec![CertificateDer::from(cert_der)],
                    PrivateKeyDer::Pkcs8(PrivatePkcs8KeyDer::from(key_der)),
                )
                .map_err(|e| format!("Cannot build bridge TLS server config: {}", e))?;
            Ok(Arc::new(config))
        })
        .clone()
}

/// Issues the companion device its own self-signed identity. The desktop is the
/// pairing authority, so it also records the matching fingerprint and later
/// refuses any other certificate from that device.
pub fn issue_client_identity() -> Result<(String, String, String), String> {
    install_crypto_provider();
    let key_pair = rcgen::KeyPair::generate()
        .map_err(|e| format!("Cannot generate companion TLS key: {}", e))?;
    let params = rcgen::CertificateParams::new(vec!["jarvis-android-bridge".to_string()])
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

pub fn pinned_client_config(fingerprint: &str) -> Result<Arc<ClientConfig>, String> {
    install_crypto_provider();
    let expected = normalize_fingerprint(fingerprint)
        .ok_or_else(|| "Invalid pinned certificate fingerprint".to_string())?;
    Ok(Arc::new(
        ClientConfig::builder()
            .dangerous()
            .with_custom_certificate_verifier(Arc::new(PinnedVerifier { expected }))
            .with_no_client_auth(),
    ))
}

#[cfg(test)]
mod tests {
    use super::{fingerprint_hex, normalize_fingerprint};

    #[test]
    fn fingerprint_normalization_accepts_colons_and_case() {
        let raw = "AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99:Aa:Bb:Cc:Dd:Ee:Ff:00:11:22:33:44:55:66:77:88:99";
        let parsed = normalize_fingerprint(raw).expect("valid fingerprint");
        assert_eq!(parsed[0], 0xaa);
        assert_eq!(parsed[31], 0x99);
    }

    #[test]
    fn fingerprint_normalization_rejects_bad_input() {
        assert!(normalize_fingerprint("deadbeef").is_none());
        assert!(normalize_fingerprint(&"z".repeat(64)).is_none());
    }

    #[test]
    fn fingerprint_is_sha256_of_der() {
        assert_eq!(fingerprint_hex(b"abc").len(), 64);
        assert_eq!(
            fingerprint_hex(b"abc"),
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
        );
    }
}
