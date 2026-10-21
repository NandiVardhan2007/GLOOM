use sha2::{Digest, Sha256};
use std::fs::File;
use std::io::Read;
use std::path::Path;
use std::process::Command;
use std::time::{SystemTime, UNIX_EPOCH};

pub const CURRENT_VERSION: &str = env!("CARGO_PKG_VERSION");
pub const MAX_UPDATE_SIZE_BYTES: u64 = 150 * 1024 * 1024; // 150 MB

pub const APPROVED_RELEASE_HOSTS: &[&str] = &[
    "github.com",
    "api.github.com",
    "objects.githubusercontent.com",
    "raw.githubusercontent.com",
];

pub fn parse_semver(version: &str) -> Option<(u32, u32, u32)> {
    let clean = version.trim().trim_start_matches('v');
    let mut parts = clean.split('.');
    let major = parts.next()?.parse().ok()?;
    let minor = parts.next()?.parse().ok()?;
    let patch_str = parts.next()?.split('-').next()?;
    let patch = patch_str.parse().ok()?;
    Some((major, minor, patch))
}

pub fn is_newer_version(current: &str, target: &str) -> bool {
    match (parse_semver(current), parse_semver(target)) {
        (Some((c_maj, c_min, c_pat)), Some((t_maj, t_min, t_pat))) => {
            (t_maj, t_min, t_pat) > (c_maj, c_min, c_pat)
        }
        _ => false,
    }
}

pub fn validate_release_url(url_str: &str) -> Result<String, String> {
    let trimmed = url_str.trim();
    if !trimmed.starts_with("https://") {
        return Err("Update rejected: URL must use secure HTTPS transport.".into());
    }
    if trimmed.chars().any(|c| c.is_control() || c.is_whitespace()) {
        return Err("Update rejected: URL contains control or whitespace characters.".into());
    }

    let without_scheme = &trimmed["https://".len()..];
    let authority_end = without_scheme
        .find(['/', '?', '#'])
        .unwrap_or(without_scheme.len());
    let authority = &without_scheme[..authority_end];
    let path = &without_scheme[authority_end..];

    if authority.contains('@') || authority.contains(':') {
        return Err("Update rejected: URL must not contain credentials or a custom port.".into());
    }

    let host_lower = authority.to_ascii_lowercase();
    let is_approved = APPROVED_RELEASE_HOSTS.iter().any(|&approved| host_lower == approved);
    if !is_approved {
        return Err(format!(
            "Update rejected: Host '{}' is not in approved release distribution list.",
            host_lower
        ));
    }

    let path_without_query = path
        .split(['?', '#'])
        .next()
        .unwrap_or("");
    let official_repo_path = match host_lower.as_str() {
        "github.com" => path_without_query.starts_with("/NandiVardhan2007/JARVIS/releases/download/"),
        "api.github.com" => path_without_query.starts_with("/repos/NandiVardhan2007/JARVIS/releases/"),
        "raw.githubusercontent.com" => path_without_query.starts_with("/NandiVardhan2007/JARVIS/"),
        "objects.githubusercontent.com" => true, // opaque CDN path; checksum is mandatory below
        _ => false,
    };
    if !official_repo_path {
        return Err("Update rejected: Release path must originate from the official JARVIS repository.".into());
    }

    Ok(trimmed.to_string())
}

pub fn compute_file_sha256(path: &Path) -> Result<String, String> {
    let mut file = File::open(path).map_err(|e| format!("Cannot open update artifact for hashing: {}", e))?;
    let mut hasher = Sha256::new();
    let mut buffer = [0u8; 65536];

    loop {
        let count = file.read(&mut buffer).map_err(|e| format!("Error reading artifact: {}", e))?;
        if count == 0 {
            break;
        }
        hasher.update(&buffer[..count]);
    }

    let result = hasher.finalize();
    Ok(format!("{:x}", result))
}

pub fn verify_pe_header(path: &Path) -> Result<(), String> {
    let mut file = File::open(path).map_err(|e| format!("Cannot open update file: {}", e))?;
    let mut header = [0u8; 2];
    let read_bytes = file.read(&mut header).map_err(|e| format!("Failed to read header: {}", e))?;
    if read_bytes < 2 || header != [0x4D, 0x5A] {
        return Err("Update artifact is not a valid Windows executable binary (missing MZ header).".into());
    }
    Ok(())
}

pub fn extract_sha256(text: &str) -> Option<String> {
    let mut hash = String::new();
    for ch in text.chars() {
        if ch.is_ascii_hexdigit() {
            hash.push(ch.to_ascii_lowercase());
            if hash.len() == 64 {
                return Some(hash);
            }
        } else {
            hash.clear();
        }
    }
    None
}

fn fetch_published_checksum(download_url: &str) -> Result<String, String> {
    let checksum_url = format!("{}.sha256", download_url);
    validate_release_url(&checksum_url)
        .map_err(|e| format!("Update rejected: no valid checksum sidecar available ({e})"))?;

    let output = Command::new("curl.exe")
        .args([
            "--proto", "=https",
            "--proto-redir", "=https",
            "--max-redirs", "5",
            "--tlsv1.2",
            "--max-filesize", "4096",
            "-sSfL",
            &checksum_url,
        ])
        .output()
        .map_err(|e| format!("Failed to fetch update checksum: {}", e))?;

    if !output.status.success() {
        return Err("Update rejected: the release does not publish a SHA-256 checksum.".into());
    }

    let text = String::from_utf8_lossy(&output.stdout);
    extract_sha256(&text)
        .ok_or_else(|| "Update rejected: published checksum file is malformed.".to_string())
}

pub fn stage_and_install_update(
    download_url: &str,
    target_version: Option<&str>,
    expected_sha256: Option<&str>,
) -> Result<String, String> {
    let validated_url = validate_release_url(download_url)?;

    // Every self-update must be integrity-verified before execution. If the caller
    // could not fetch the checksum (e.g. webview CORS), fetch the published sidecar.
    let expected_hash = match expected_sha256.map(str::trim).filter(|s| !s.is_empty()) {
        Some(provided) => provided.to_ascii_lowercase(),
        None => fetch_published_checksum(&validated_url)?,
    };

    if let Some(target) = target_version {
        let clean_target = target.trim().trim_start_matches('v');
        let clean_current = CURRENT_VERSION.trim().trim_start_matches('v');
        if !is_newer_version(CURRENT_VERSION, target) && clean_target != clean_current {
            return Err(format!(
                "Downgrade protection: Cannot install version {} because it is not newer than current version {}.",
                target, CURRENT_VERSION
            ));
        }
    }

    let current_exe = std::env::current_exe()
        .map_err(|e| format!("Failed to resolve current application executable path: {}", e))?;

    let timestamp = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap_or_default()
        .as_millis();

    let temp_dir = std::env::temp_dir();
    let staged_path = temp_dir.join(format!("jarvis_update_{}.exe.staged", timestamp));

    // Download to staged path using curl
    let staged_str = staged_path
        .to_str()
        .ok_or_else(|| "Update staging path is not valid UTF-8".to_string())?;
    let status = Command::new("curl.exe")
        .args([
            "--proto", "=https",
            "--proto-redir", "=https",
            "--max-redirs", "5",
            "--tlsv1.2",
            "-sSfL",
            "-o", staged_str,
            &validated_url,
        ])
        .status()
        .map_err(|e| format!("Failed to execute secure download client: {}", e))?;

    if !status.success() {
        let _ = std::fs::remove_file(&staged_path);
        return Err("Failed to download update artifact from approved release server.".into());
    }

    let meta = std::fs::metadata(&staged_path)
        .map_err(|e| format!("Cannot inspect staged update file: {}", e))?;
    if meta.len() == 0 || meta.len() > MAX_UPDATE_SIZE_BYTES {
        let _ = std::fs::remove_file(&staged_path);
        return Err(format!(
            "Update artifact size ({} bytes) violates acceptable distribution boundaries.",
            meta.len()
        ));
    }

    // Verify valid PE header
    if let Err(e) = verify_pe_header(&staged_path) {
        let _ = std::fs::remove_file(&staged_path);
        return Err(e);
    }

    // Verify SHA-256 checksum
    let calculated_hash = compute_file_sha256(&staged_path)?;
    if expected_hash != calculated_hash {
        let _ = std::fs::remove_file(&staged_path);
        return Err(format!(
            "SHA-256 integrity check failed! Expected: {}, Computed: {}. Update aborted.",
            expected_hash, calculated_hash
        ));
    }

    // Prepare atomic replacement helper script with rollback mechanism
    #[cfg(windows)]
    {
        use std::os::windows::process::CommandExt;
        const CREATE_NO_WINDOW: u32 = 0x08000000;

        let script_path = temp_dir.join(format!("jarvis_apply_update_{}.cmd", timestamp));
        let current_pid = std::process::id();
        let current_exe_str = current_exe.to_string_lossy();
        let staged_str = staged_path.to_string_lossy();
        let backup_str = format!("{}.old", current_exe_str);

        let script_content = format!(
            r#"@echo off
setlocal
taskkill /f /pid {current_pid} >nul 2>&1
timeout /t 2 /nobreak >nul

set RETRIES=10
:RETRY_REN
ren "{current_exe_str}" "{backup_str}" >nul 2>&1
if errorlevel 1 (
    timeout /t 1 /nobreak >nul
    set /a RETRIES-=1
    if %RETRIES% gtr 0 goto RETRY_REN
)

move /y "{staged_str}" "{current_exe_str}" >nul 2>&1
if errorlevel 1 (
    ren "{backup_str}" "{current_exe_str}" >nul 2>&1
    exit /b 1
)
del /f /q "{backup_str}" >nul 2>&1
start "" "{current_exe_str}"
del "%~f0" >nul 2>&1
"#,
            current_pid = current_pid,
            current_exe_str = current_exe_str,
            backup_str = backup_str,
            staged_str = staged_str
        );

        std::fs::write(&script_path, script_content)
            .map_err(|e| format!("Failed to create update applier script: {}", e))?;

        Command::new("cmd.exe")
            .args(["/c", script_path.to_str().unwrap()])
            .creation_flags(CREATE_NO_WINDOW)
            .spawn()
            .map_err(|e| format!("Failed to launch update installer: {}", e))?;
    }

    #[cfg(not(windows))]
    {
        return Err("Self-updating executable swap is only supported on Windows.".into());
    }

    Ok("Update verified and staged successfully. Restarting application.".into())
}

#[cfg(test)]
mod tests {
    use super::{is_newer_version, parse_semver, validate_release_url};

    #[test]
    fn accepts_official_release_download() {
        let url = "https://github.com/NandiVardhan2007/JARVIS/releases/download/v1.0.5/JARVIS-Island-Windows.exe";
        assert!(validate_release_url(url).is_ok());
    }

    #[test]
    fn rejects_plain_http() {
        let url = "http://github.com/NandiVardhan2007/JARVIS/releases/download/v1.0.5/a.exe";
        assert!(validate_release_url(url).is_err());
    }

    #[test]
    fn rejects_lookalike_hosts() {
        for url in [
            "https://github.com.evil.example/NandiVardhan2007/JARVIS/releases/download/v1/a.exe",
            "https://evilgithub.com/NandiVardhan2007/JARVIS/releases/download/v1/a.exe",
            "https://github.com@evil.example/NandiVardhan2007/JARVIS/releases/download/v1/a.exe",
        ] {
            assert!(validate_release_url(url).is_err(), "should reject {url}");
        }
    }

    #[test]
    fn rejects_other_repositories() {
        let url = "https://github.com/attacker/JARVIS/releases/download/v1.0.5/a.exe";
        assert!(validate_release_url(url).is_err());
    }

    #[test]
    fn rejects_credentials_and_ports() {
        assert!(validate_release_url("https://user:pw@github.com/NandiVardhan2007/JARVIS/releases/download/v1/a.exe").is_err());
        assert!(validate_release_url("https://github.com:8443/NandiVardhan2007/JARVIS/releases/download/v1/a.exe").is_err());
    }

    #[test]
    fn parses_published_checksum_files() {
        let hash = "a".repeat(64);
        assert_eq!(
            super::extract_sha256(&format!("{}  JARVIS-Island-Windows.exe\n", hash)),
            Some(hash.clone())
        );
        assert_eq!(super::extract_sha256("not a checksum"), None);
    }

    #[test]
    fn semver_comparison() {
        assert!(is_newer_version("1.0.2", "1.0.10"));
        assert!(!is_newer_version("1.0.10", "1.0.2"));
        assert!(!is_newer_version("1.0.0", "v1.0.0"));
        assert_eq!(parse_semver("v1.2.3-beta"), Some((1, 2, 3)));
    }
}
