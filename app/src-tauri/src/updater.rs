use sha2::{Digest, Sha256};
use std::fs::File;
use std::io::Read;
use std::path::Path;
use std::process::Command;
use std::time::{SystemTime, UNIX_EPOCH};

pub const CURRENT_VERSION: &str = "1.0.7";
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

    let without_scheme = &trimmed["https://".len()..];
    let host_end = without_scheme.find('/').unwrap_or(without_scheme.len());
    let host = &without_scheme[..host_end];

    let host_lower = host.to_ascii_lowercase();
    let is_approved = APPROVED_RELEASE_HOSTS.iter().any(|&approved| {
        host_lower == approved || host_lower.ends_with(&format!(".{}", approved))
    });

    if !is_approved {
        return Err(format!(
            "Update rejected: Host '{}' is not in approved release distribution list.",
            host_lower
        ));
    }

    if host_lower == "github.com" && !trimmed.contains("/NandiVardhan2007/JARVIS/releases/download/") {
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

pub fn stage_and_install_update(
    download_url: &str,
    target_version: Option<&str>,
    expected_sha256: Option<&str>,
) -> Result<String, String> {
    let validated_url = validate_release_url(download_url)?;

    if let Some(target) = target_version {
        if !is_newer_version(CURRENT_VERSION, target) {
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
    let status = Command::new("curl.exe")
        .args([
            "--proto", "=https",
            "--tlsv1.2",
            "-sSfL",
            "-o", staged_path.to_str().unwrap(),
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

    // Verify SHA-256 checksum if provided
    let calculated_hash = compute_file_sha256(&staged_path)?;
    if let Some(expected) = expected_sha256 {
        let clean_expected = expected.trim().to_ascii_lowercase();
        if clean_expected != calculated_hash {
            let _ = std::fs::remove_file(&staged_path);
            return Err(format!(
                "SHA-256 integrity check failed! Expected: {}, Computed: {}. Update aborted.",
                clean_expected, calculated_hash
            ));
        }
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
timeout /t 1 /nobreak >nul
taskkill /f /pid {current_pid} >nul 2>&1
ren "{current_exe_str}" "{backup_str}" >nul 2>&1
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
