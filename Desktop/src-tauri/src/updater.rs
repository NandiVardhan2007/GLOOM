use std::path::PathBuf;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Mutex;
use std::time::{Duration, SystemTime, UNIX_EPOCH};

use serde::{Deserialize, Serialize};
use tauri::{AppHandle, Emitter, Manager};

/// Minimum time between background update checks. Manual checks bypass this.
const CHECK_INTERVAL_SECS: i64 = 24 * 60 * 60;
/// Network timeout for a single manifest or release request.
const CHECK_TIMEOUT_SECS: u64 = 15;
/// A release must be at least this old before auto-update installs it, so a
/// broken release cannot reach everyone within minutes of being published.
const MIN_AUTO_INSTALL_AGE_SECS: i64 = 24 * 60 * 60;
const STATE_FILE: &str = "update-state.json";
const GITHUB_LATEST_API: &str = "https://api.github.com/repos/NandiVardhan2007/GLOOM/releases/latest";
const GITHUB_ALL_RELEASES_API: &str = "https://api.github.com/repos/NandiVardhan2007/GLOOM/releases?per_page=10";
const RAW_LATEST_JSON: &str = "https://raw.githubusercontent.com/NandiVardhan2007/GLOOM/main/build-artifacts/latest.json";
const RELEASES_PAGE: &str = "https://github.com/NandiVardhan2007/GLOOM/releases/latest";

/// Serializes manifest requests so concurrent callers share a single network hit.
static CHECK_LOCK: tokio::sync::Mutex<()> = tokio::sync::Mutex::const_new(());
static UPDATE_BUSY: AtomicBool = AtomicBool::new(false);
static LAST_CHECK: Mutex<Option<UpdateCheckResult>> = Mutex::new(None);

#[derive(Clone, Default, Serialize, Deserialize, Debug, PartialEq)]
pub struct UpdateCheckResult {
    pub available: bool,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub version: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub date: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub body: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub download_url: Option<String>,
}

#[derive(Default, Serialize, Deserialize)]
struct PersistedUpdateState {
    last_check: i64,
    app_version: String,
    version: String,
    date: String,
    #[serde(default)]
    channel: String,
    #[serde(default)]
    download_url: Option<String>,
}

/// Parses a semver-like version string into a (major, minor, patch) tuple.
pub fn parse_version_tuple(v: &str) -> Option<(u64, u64, u64)> {
    let clean = v.trim().trim_start_matches(|c| c == 'v' || c == 'V');
    let mut parts = clean.split('.');
    let major = parts.next()?.parse().ok()?;
    let minor = parts.next().unwrap_or("0").parse().ok()?;
    let patch_part = parts.next().unwrap_or("0");
    let patch_digits: String = patch_part.chars().take_while(|c| c.is_ascii_digit()).collect();
    let patch = if patch_digits.is_empty() {
        0
    } else {
        patch_digits.parse().ok()?
    };
    Some((major, minor, patch))
}

/// Returns true if candidate is strictly newer than current version.
pub fn is_newer_version(current: &str, candidate: &str) -> bool {
    let Some(curr) = parse_version_tuple(current) else {
        return false;
    };
    let Some(cand) = parse_version_tuple(candidate) else {
        return false;
    };
    cand > curr
}

/// Parses either a GitHub API release object/array or a latest.json manifest.
pub fn parse_release_json(val: &serde_json::Value, current_version: &str) -> Option<UpdateCheckResult> {
    if let Some(arr) = val.as_array() {
        for item in arr {
            if let Some(res) = parse_single_release_json(item, current_version) {
                if res.available {
                    return Some(res);
                }
            }
        }
        return Some(UpdateCheckResult::default());
    }
    parse_single_release_json(val, current_version)
}

fn parse_single_release_json(val: &serde_json::Value, current_version: &str) -> Option<UpdateCheckResult> {
    // 1. GitHub API format (release object)
    if let Some(tag) = val.get("tag_name").and_then(|v| v.as_str()) {
        let is_draft = val.get("draft").and_then(|v| v.as_bool()).unwrap_or(false);
        if is_draft {
            return None;
        }

        let newer = is_newer_version(current_version, tag);
        let date = val.get("published_at").and_then(|v| v.as_str()).map(ToString::to_string);
        let body = val.get("body").and_then(|v| v.as_str()).map(ToString::to_string);

        let mut download_url = None;
        if let Some(assets) = val.get("assets").and_then(|v| v.as_array()) {
            // First priority: GLOOM-Setup.exe
            for asset in assets {
                let name = asset.get("name").and_then(|v| v.as_str()).unwrap_or("");
                if name.eq_ignore_ascii_case("GLOOM-Setup.exe") {
                    if let Some(url) = asset.get("browser_download_url").and_then(|v| v.as_str()) {
                        download_url = Some(url.to_string());
                        break;
                    }
                }
            }
            // Second priority: GLOOM-Setup-*.exe
            if download_url.is_none() {
                for asset in assets {
                    let name = asset.get("name").and_then(|v| v.as_str()).unwrap_or("");
                    if name.starts_with("GLOOM-Setup") && name.ends_with(".exe") {
                        if let Some(url) = asset.get("browser_download_url").and_then(|v| v.as_str()) {
                            download_url = Some(url.to_string());
                            break;
                        }
                    }
                }
            }
            // Third priority: any installer .exe (excluding portable and uninstaller)
            if download_url.is_none() {
                for asset in assets {
                    let name = asset.get("name").and_then(|v| v.as_str()).unwrap_or("").to_lowercase();
                    if name.ends_with(".exe") && !name.contains("portable") && !name.contains("uninstall") {
                        if let Some(url) = asset.get("browser_download_url").and_then(|v| v.as_str()) {
                            download_url = Some(url.to_string());
                            break;
                        }
                    }
                }
            }
        }

        let clean_version = tag.trim_start_matches(|c| c == 'v' || c == 'V').to_string();
        return Some(UpdateCheckResult {
            available: newer,
            version: Some(clean_version),
            date,
            body,
            download_url,
        });
    }

    // 2. latest.json format
    if let Some(version) = val.get("version").and_then(|v| v.as_str()) {
        let newer = is_newer_version(current_version, version);
        let date = val.get("pub_date").and_then(|v| v.as_str()).map(ToString::to_string);
        let body = val.get("notes").and_then(|v| v.as_str()).map(ToString::to_string);
        let download_url = val
            .get("platforms")
            .and_then(|p| p.get("windows-x86_64"))
            .and_then(|w| w.get("url"))
            .and_then(|u| u.as_str())
            .map(ToString::to_string);

        let clean_version = version.trim_start_matches(|c| c == 'v' || c == 'V').to_string();
        return Some(UpdateCheckResult {
            available: newer,
            version: Some(clean_version),
            date,
            body,
            download_url,
        });
    }

    None
}

fn prerelease_channel(app: &AppHandle) -> bool {
    crate::utils::get_setting_str(app, "gloom-update-channel").as_deref() == Some("prerelease")
}

fn channel_name(app: &AppHandle) -> &'static str {
    if prerelease_channel(app) { "prerelease" } else { "stable" }
}

fn now_secs() -> i64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_secs() as i64)
        .unwrap_or(0)
}

fn state_path(app: &AppHandle) -> Option<PathBuf> {
    app.path()
        .app_config_dir()
        .ok()
        .map(|dir| dir.join(STATE_FILE))
}

fn read_state(app: &AppHandle) -> PersistedUpdateState {
    state_path(app)
        .and_then(|path| std::fs::read_to_string(path).ok())
        .and_then(|content| serde_json::from_str(&content).ok())
        .unwrap_or_default()
}

fn write_state(app: &AppHandle, state: &PersistedUpdateState) {
    if let Some(path) = state_path(app) {
        if let Some(parent) = path.parent() {
            let _ = std::fs::create_dir_all(parent);
        }
        if let Ok(content) = serde_json::to_string(state) {
            let _ = std::fs::write(path, content);
        }
    }
}

fn result_from_state(state: &PersistedUpdateState) -> UpdateCheckResult {
    if state.version.is_empty() {
        return UpdateCheckResult::default();
    }
    UpdateCheckResult {
        available: true,
        version: Some(state.version.clone()),
        date: (!state.date.is_empty()).then(|| state.date.clone()),
        body: None,
        download_url: state.download_url.clone(),
    }
}

/// Returns a cached result only when the last check is recent and was made
/// against the version currently running (so updating invalidates the cache).
fn cached_result(app: &AppHandle) -> Option<UpdateCheckResult> {
    let state = read_state(app);
    if state.last_check == 0 || now_secs() - state.last_check >= CHECK_INTERVAL_SECS {
        return None;
    }
    if state.app_version != app.package_info().version.to_string() || state.channel != channel_name(app) {
        return None;
    }
    Some(result_from_state(&state))
}

pub fn release_is_old_enough(result: &UpdateCheckResult) -> bool {
    match result.date.as_deref().and_then(parse_rfc3339_utc) {
        Some(published) => now_secs() - published >= MIN_AUTO_INSTALL_AGE_SECS,
        None => false,
    }
}

/// Checks for an update at most once per `CHECK_INTERVAL_SECS` unless `force`
/// is set. Queries GitHub Releases API directly with raw manifest fallback.
pub async fn check(app: &AppHandle, force: bool) -> Result<UpdateCheckResult, String> {
    if !force {
        if let Some(cached) = cached_result(app) {
            return Ok(cached);
        }
        #[cfg(debug_assertions)]
        {
            return Ok(UpdateCheckResult::default());
        }
    }

    let _guard = CHECK_LOCK.lock().await;

    // Another caller may have completed a check while we waited for the lock.
    if !force {
        if let Some(cached) = cached_result(app) {
            return Ok(cached);
        }
    }

    let current_version = app.package_info().version.to_string();
    let is_prerelease = prerelease_channel(app);

    let client = reqwest::Client::builder()
        .user_agent(format!("GLOOM/{}", current_version))
        .timeout(Duration::from_secs(CHECK_TIMEOUT_SECS))
        .build()
        .map_err(|e| format!("failed to build HTTP client: {e}"))?;

    let url = if is_prerelease {
        GITHUB_ALL_RELEASES_API
    } else {
        GITHUB_LATEST_API
    };

    let response = client.get(url).send().await;

    let body = match response {
        Ok(res) if res.status().is_success() => res.text().await.ok(),
        _ => {
            crate::diagnostics::log("update: github api rate limit or error, falling back to raw latest.json");
            if let Ok(fallback_res) = client.get(RAW_LATEST_JSON).send().await {
                if fallback_res.status().is_success() {
                    fallback_res.text().await.ok()
                } else {
                    None
                }
            } else {
                None
            }
        }
    };

    let Some(raw_text) = body else {
        return Err("failed to fetch release information from GitHub".to_string());
    };

    let val: serde_json::Value = serde_json::from_str(&raw_text)
        .map_err(|e| format!("failed to parse update JSON: {e}"))?;

    let result = parse_release_json(&val, &current_version).unwrap_or_default();

    write_state(
        app,
        &PersistedUpdateState {
            last_check: now_secs(),
            app_version: current_version,
            version: if result.available { result.version.clone().unwrap_or_default() } else { String::new() },
            date: if result.available { result.date.clone().unwrap_or_default() } else { String::new() },
            channel: channel_name(app).to_string(),
            download_url: result.download_url.clone(),
        },
    );

    if let Ok(mut cached) = LAST_CHECK.lock() {
        *cached = Some(result.clone());
    }

    Ok(result)
}

/// Downloads and installs the available update via custom setup wizard.
pub async fn install(app: &AppHandle) -> Result<(), String> {
    if !is_installed_copy() {
        crate::diagnostics::log("update: this copy is not installed (portable or development build), opening the release page instead of running the installer");
        let _ = tauri_plugin_opener::open_url(RELEASES_PAGE, None::<&str>);
        let _ = app.emit("auto-update-status", serde_json::json!({ "status": "done" }));
        return Ok(());
    }
    if UPDATE_BUSY.swap(true, Ordering::SeqCst) {
        return Err("an update is already in progress".to_string());
    }
    let result = install_inner(app).await;
    UPDATE_BUSY.store(false, Ordering::SeqCst);
    if let Err(e) = &result {
        crate::diagnostics::log(&format!("update install failed: {e}"));
    }
    result
}

fn is_installed_copy() -> bool {
    std::env::current_exe()
        .ok()
        .and_then(|p| p.parent().map(PathBuf::from))
        .is_some_and(|dir| crate::uninstall_registry::install_scope(&dir).is_some())
}

fn install_needs_unavailable_elevation() -> bool {
    let Some(dir) = std::env::current_exe().ok().and_then(|p| p.parent().map(PathBuf::from)) else { return false };
    crate::uninstall_registry::install_scope(&dir) == Some(true) && !crate::native_uninstall::is_elevated()
}

async fn install_inner(app: &AppHandle) -> Result<(), String> {
    let _guard = CHECK_LOCK.lock().await;

    let update_info = {
        let cached = LAST_CHECK.lock().ok().and_then(|c| c.clone());
        match cached {
            Some(res) if res.available && res.download_url.is_some() => res,
            _ => check(app, true).await?,
        }
    };

    if !update_info.available {
        return Err("no update available".to_string());
    }

    let version = update_info.version.unwrap_or_else(|| "latest".to_string());
    let download_url = update_info.download_url.unwrap_or_else(|| {
        format!(
            "https://github.com/NandiVardhan2007/GLOOM/releases/download/v{}/GLOOM-Setup.exe",
            version
        )
    });

    crate::diagnostics::log(&format!("update: downloading installer from {download_url}"));

    let client = reqwest::Client::builder()
        .user_agent(format!("GLOOM/{}", app.package_info().version))
        .timeout(Duration::from_secs(300))
        .build()
        .map_err(|e| format!("failed to build download client: {e}"))?;

    let mut response = client
        .get(&download_url)
        .send()
        .await
        .map_err(|e| format!("failed to connect to download URL: {e}"))?;

    if !response.status().is_success() {
        return Err(format!("download failed with status {}", response.status()));
    }

    let total_size = response.content_length();
    let temp_dir = std::env::temp_dir();
    let temp_installer = temp_dir.join("GLOOM-Setup-Update.exe");

    let mut file = std::fs::File::create(&temp_installer)
        .map_err(|e| format!("failed to create temp installer file at {}: {e}", temp_installer.display()))?;

    let _ = app.emit("auto-update-status", serde_json::json!({
        "status": "downloading",
        "progress": 0
    }));

    let mut downloaded: u64 = 0;
    let mut last_percent: u32 = 0;

    while let Some(chunk) = response.chunk().await.map_err(|e| format!("stream error: {e}"))? {
        use std::io::Write;
        file.write_all(&chunk)
            .map_err(|e| format!("failed to write installer chunk: {e}"))?;
        downloaded += chunk.len() as u64;

        if let Some(total) = total_size {
            if total > 0 {
                let percent = ((downloaded as f64 / total as f64) * 100.0).clamp(0.0, 100.0) as u32;
                if percent != last_percent {
                    last_percent = percent;
                    let _ = app.emit("auto-update-status", serde_json::json!({
                        "status": "downloading",
                        "progress": percent
                    }));
                }
            }
        }
    }

    use std::io::Write;
    file.flush().map_err(|e| format!("failed to flush installer: {e}"))?;
    drop(file);

    crate::diagnostics::log("update: download complete, launching installer");

    let _ = app.emit("auto-update-status", serde_json::json!({
        "status": "installing",
        "progress": 100
    }));

    let current_exe = std::env::current_exe().map_err(|e| e.to_string())?;
    let install_dir = current_exe.parent().unwrap_or(std::path::Path::new(""));
    let all_users = crate::uninstall_registry::install_scope(install_dir).unwrap_or(false);

    let mut cmd = std::process::Command::new(&temp_installer);
    cmd.arg("--auto-install");
    cmd.arg(format!("--install-dir={}", install_dir.display()));
    if all_users {
        cmd.arg("--all-users");
    }

    #[cfg(windows)]
    {
        use std::os::windows::process::CommandExt;
        const CREATE_BREAKAWAY_FROM_JOB: u32 = 0x01000000;
        cmd.creation_flags(CREATE_BREAKAWAY_FROM_JOB);
    }

    cmd.spawn().map_err(|e| format!("failed to spawn installer: {e}"))?;

    crate::state::SHUTTING_DOWN.store(true, Ordering::Relaxed);
    app.cleanup_before_exit();
    std::process::exit(0);
}

/// Startup entry point: always checks so the UI can show an update badge, and
/// auto-installs only when the user enabled it and the release has aged.
pub async fn run_startup_check(app: AppHandle) {
    let auto_update = crate::utils::get_setting_str(&app, "gloom-auto-update").as_deref() == Some("true");

    if auto_update {
        let _ = app.emit("auto-update-status", serde_json::json!({ "status": "checking" }));
    }

    let ready_to_install = |result: &UpdateCheckResult| prerelease_channel(&app) || release_is_old_enough(result);

    let result = match check(&app, false).await {
        Ok(result) => result,
        Err(_) => {
            if auto_update {
                let _ = app.emit("auto-update-status", serde_json::json!({ "status": "done" }));
            }
            return;
        }
    };

    let _ = app.emit("update-available", &result);

    if !result.available {
        if auto_update {
            let _ = app.emit("auto-update-status", serde_json::json!({ "status": "done" }));
        }
        return;
    }

    if auto_update && ready_to_install(&result) && !is_installed_copy() {
        crate::diagnostics::log("auto-update: skipping silent install, this copy is not installed (portable or development build); leaving update available");
        let _ = app.emit("auto-update-status", serde_json::json!({ "status": "done" }));
    } else if auto_update && ready_to_install(&result) && install_needs_unavailable_elevation() {
        crate::diagnostics::log("auto-update: skipping silent install, all-users install needs elevation this process doesn't have; leaving update available for manual install");
        let _ = app.emit("auto-update-status", serde_json::json!({ "status": "done" }));
    } else if auto_update && ready_to_install(&result) {
        if install(&app).await.is_err() {
            let _ = app.emit("auto-update-status", serde_json::json!({ "status": "done" }));
        }
    } else if auto_update {
        let _ = app.emit("auto-update-status", serde_json::json!({ "status": "done" }));
    }
}

#[tauri::command]
pub async fn check_for_updates(app: AppHandle, force: bool) -> Result<UpdateCheckResult, String> {
    let result = check(&app, force).await?;
    let _ = app.emit("update-available", &result);
    Ok(result)
}

#[tauri::command]
pub async fn install_update(app: AppHandle) -> Result<(), String> {
    install(&app).await
}

#[tauri::command]
pub fn get_update_state(app: AppHandle) -> UpdateCheckResult {
    if let Ok(cached) = LAST_CHECK.lock() {
        if let Some(result) = cached.as_ref() {
            return result.clone();
        }
    }
    result_from_state(&read_state(&app))
}

fn parse_rfc3339_utc(value: &str) -> Option<i64> {
    if value.len() < 20 || !value.ends_with('Z') {
        return None;
    }
    let bytes = value.as_bytes();
    if bytes[4] != b'-' || bytes[7] != b'-' || bytes[10] != b'T' {
        return None;
    }
    let year: i64 = value.get(0..4)?.parse().ok()?;
    let month: i64 = value.get(5..7)?.parse().ok()?;
    let day: i64 = value.get(8..10)?.parse().ok()?;
    let hour: i64 = value.get(11..13)?.parse().ok()?;
    let minute: i64 = value.get(14..16)?.parse().ok()?;
    let second: i64 = value.get(17..19)?.parse().ok()?;
    if !(1..=12).contains(&month) || !(1..=31).contains(&day) || hour > 23 || minute > 59 || second > 60 {
        return None;
    }
    let days = days_from_civil(year, month, day);
    Some(days * 86_400 + hour * 3_600 + minute * 60 + second)
}

/// Howard Hinnant's days-from-civil algorithm.
fn days_from_civil(year: i64, month: i64, day: i64) -> i64 {
    let year = if month <= 2 { year - 1 } else { year };
    let era = if year >= 0 { year } else { year - 399 } / 400;
    let year_of_era = year - era * 400;
    let shifted_month = if month > 2 { month - 3 } else { month + 9 };
    let day_of_year = (153 * shifted_month + 2) / 5 + day - 1;
    let day_of_era = year_of_era * 365 + year_of_era / 4 - year_of_era / 100 + day_of_year;
    era * 146_097 + day_of_era - 719_468
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_tauri_action_pub_date() {
        assert_eq!(parse_rfc3339_utc("1970-01-01T00:00:00Z"), Some(0));
        assert_eq!(parse_rfc3339_utc("2026-09-13T18:14:46Z"), Some(1_789_323_286));
        assert_eq!(parse_rfc3339_utc("2026-09-13T18:14:46.123Z"), Some(1_789_323_286));
    }

    #[test]
    fn rejects_non_utc_and_malformed_dates() {
        assert_eq!(parse_rfc3339_utc("2026-09-13T18:14:46+02:00"), None);
        assert_eq!(parse_rfc3339_utc("not-a-date"), None);
        assert_eq!(parse_rfc3339_utc("2026-13-40T99:99:99Z"), None);
    }

    #[test]
    fn computes_civil_days() {
        assert_eq!(days_from_civil(1970, 1, 1), 0);
        assert_eq!(days_from_civil(2026, 9, 13), 20_709);
    }

    #[test]
    fn parses_version_tuples_correctly() {
        assert_eq!(parse_version_tuple("2.7.0"), Some((2, 7, 0)));
        assert_eq!(parse_version_tuple("v2.7.1"), Some((2, 7, 1)));
        assert_eq!(parse_version_tuple("V3.0.12-beta.1"), Some((3, 0, 12)));
        assert_eq!(parse_version_tuple("1.0"), Some((1, 0, 0)));
        assert_eq!(parse_version_tuple("invalid"), None);
    }

    #[test]
    fn compares_versions_correctly() {
        assert!(is_newer_version("2.7.0", "2.7.1"));
        assert!(is_newer_version("2.7.0", "v2.7.1"));
        assert!(is_newer_version("2.7.1", "2.8.0"));
        assert!(is_newer_version("2.7.1", "3.0.0"));
        assert!(!is_newer_version("2.7.1", "2.7.1"));
        assert!(!is_newer_version("2.7.1", "v2.7.1"));
        assert!(!is_newer_version("2.7.1", "2.7.0"));
        assert!(!is_newer_version("3.0.0", "2.9.9"));
    }

    #[test]
    fn parses_github_release_payload() {
        let json = serde_json::json!({
            "tag_name": "v2.8.0",
            "published_at": "2026-11-20T12:00:00Z",
            "body": "Awesome update notes",
            "draft": false,
            "assets": [
                {
                    "name": "GLOOM-2.8.0-portable.zip",
                    "browser_download_url": "https://example.com/portable.zip"
                },
                {
                    "name": "GLOOM-Setup.exe",
                    "browser_download_url": "https://example.com/GLOOM-Setup.exe"
                }
            ]
        });

        let res = parse_release_json(&json, "2.7.1").expect("should parse");
        assert!(res.available);
        assert_eq!(res.version.as_deref(), Some("2.8.0"));
        assert_eq!(res.download_url.as_deref(), Some("https://example.com/GLOOM-Setup.exe"));
        assert_eq!(res.body.as_deref(), Some("Awesome update notes"));
    }

    #[test]
    fn parses_latest_json_manifest() {
        let json = serde_json::json!({
            "version": "v2.8.0",
            "pub_date": "2026-11-20T12:00:00Z",
            "notes": "Manifest update notes",
            "platforms": {
                "windows-x86_64": {
                    "url": "https://example.com/GLOOM-Setup-2.8.0.exe"
                }
            }
        });

        let res = parse_release_json(&json, "2.7.1").expect("should parse");
        assert!(res.available);
        assert_eq!(res.version.as_deref(), Some("2.8.0"));
        assert_eq!(res.download_url.as_deref(), Some("https://example.com/GLOOM-Setup-2.8.0.exe"));
    }
}
