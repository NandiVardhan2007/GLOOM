use std::path::PathBuf;
use std::sync::atomic::{AtomicBool, AtomicU64, Ordering};
use std::sync::{Arc, Mutex};
use std::time::{Duration, SystemTime, UNIX_EPOCH};

use serde::{Deserialize, Serialize};
use tauri::{AppHandle, Emitter, Manager};
use tauri_plugin_updater::UpdaterExt;

/// Minimum time between background update checks. Manual checks bypass this.
const CHECK_INTERVAL_SECS: i64 = 24 * 60 * 60;
/// Network timeout for a single manifest request. Generous on purpose: a
/// stalled TCP connect to github.com can burn ~21s in SYN retries before
/// succeeding, and the previous 10s cap made every check time out on such
/// routes (no update found / no badge).
const CHECK_TIMEOUT_SECS: u64 = 60;
/// A release must be at least this old before auto-update installs it, so a
/// broken release cannot reach everyone within minutes of being published.
const MIN_AUTO_INSTALL_AGE_SECS: i64 = 24 * 60 * 60;
/// Minimum time before an auto-install retries a version whose installer was
/// already launched. Without it, a failed install would re-download and exit
/// on every startup, because the process exits from inside the install path.
const AUTO_INSTALL_RETRY_SECS: i64 = 24 * 60 * 60;
const STATE_FILE: &str = "update-state.json";

/// Serializes manifest requests so concurrent callers share a single network hit.
static CHECK_LOCK: tokio::sync::Mutex<()> = tokio::sync::Mutex::const_new(());
static UPDATE_BUSY: AtomicBool = AtomicBool::new(false);
static LAST_CHECK: Mutex<Option<UpdateCheckResult>> = Mutex::new(None);
static GITHUB_DOWNLOAD_URL: Mutex<Option<String>> = Mutex::new(None);

fn is_newer_semver(candidate: &str, current: &str) -> bool {
    let parse = |s: &str| -> Vec<u32> {
        s.trim()
            .trim_start_matches('v')
            .trim_start_matches('V')
            .split('-')
            .next()
            .unwrap_or("")
            .split('.')
            .map(|p| p.parse::<u32>().unwrap_or(0))
            .collect()
    };
    let cand = parse(candidate);
    let curr = parse(current);
    let max_len = cand.len().max(curr.len());
    for i in 0..max_len {
        let c = *cand.get(i).unwrap_or(&0);
        let cur = *curr.get(i).unwrap_or(&0);
        if c != cur {
            return c > cur;
        }
    }
    false
}

async fn check_github_releases(
    current_version: &str,
) -> Option<(String, Option<String>, Option<String>, Option<String>)> {
    let client = reqwest::Client::builder()
        .timeout(Duration::from_secs(15))
        .user_agent("GLOOM-Desktop-Updater")
        .build()
        .ok()?;

    let endpoints = [
        "https://api.github.com/repos/NandiVardhan2007/GLOOM/releases/latest",
        "https://api.github.com/repos/NandiVardhan2007/JARVIS/releases/latest",
    ];

    for endpoint in endpoints {
        let resp = match client.get(endpoint).send().await {
            Ok(r) if r.status().is_success() => r,
            _ => continue,
        };

        let json: serde_json::Value = match resp.json().await {
            Ok(j) => j,
            Err(_) => continue,
        };

        let tag_name = json.get("tag_name")?.as_str()?.to_string();
        let pub_date = json
            .get("published_at")
            .and_then(|v| v.as_str())
            .map(str::to_string);
        let body = json
            .get("body")
            .and_then(|v| v.as_str())
            .map(str::to_string);

        let mut download_url = None;
        if let Some(assets) = json.get("assets").and_then(|a| a.as_array()) {
            for asset in assets {
                if let Some(name) = asset.get("name").and_then(|n| n.as_str()) {
                    if name.ends_with(".exe") && !name.ends_with(".sha256") {
                        if let Some(url) =
                            asset.get("browser_download_url").and_then(|u| u.as_str())
                        {
                            download_url = Some(url.to_string());
                            break;
                        }
                    }
                }
            }
        }

        if is_newer_semver(&tag_name, current_version) {
            return Some((tag_name, pub_date, body, download_url));
        } else {
            return None;
        }
    }
    None
}

async fn install_from_url(app: &AppHandle, url: &str) -> Result<(), String> {
    use futures_util::StreamExt;

    let _ = app.emit(
        "auto-update-status",
        serde_json::json!({ "status": "downloading", "progress": 0 }),
    );

    let client = reqwest::Client::builder()
        .user_agent("GLOOM-Desktop-Updater")
        .build()
        .map_err(|e| e.to_string())?;

    let res = client.get(url).send().await.map_err(|e| e.to_string())?;
    if !res.status().is_success() {
        return Err(format!("Download failed with status: {}", res.status()));
    }

    let total_size = res.content_length().unwrap_or(0);
    let temp_dir = std::env::temp_dir();
    let installer_path = temp_dir.join("GLOOM-Island-Setup.exe");
    let mut file = tokio::fs::File::create(&installer_path)
        .await
        .map_err(|e| e.to_string())?;

    let mut stream = res.bytes_stream();
    let mut downloaded: u64 = 0;

    use tokio::io::AsyncWriteExt;
    while let Some(chunk) = stream.next().await {
        let chunk = chunk.map_err(|e| e.to_string())?;
        file.write_all(&chunk).await.map_err(|e| e.to_string())?;
        downloaded += chunk.len() as u64;

        if total_size > 0 {
            let progress = (downloaded.saturating_mul(100) / total_size) as u32;
            let _ = app.emit(
                "auto-update-status",
                serde_json::json!({ "status": "downloading", "progress": progress }),
            );
        }
    }
    file.flush().await.map_err(|e| e.to_string())?;

    let _ = app.emit(
        "auto-update-status",
        serde_json::json!({ "status": "installing" }),
    );

    #[cfg(windows)]
    {
        crate::state::SHUTTING_DOWN.store(true, Ordering::Relaxed);
        crate::commands::release_appbars(app);
        crate::utils::set_taskbar_visibility(true, true);
        crate::services::set_native_osd_suppressed(false);
        app.cleanup_before_exit();

        let _ = std::process::Command::new(&installer_path)
            .args(["/SILENT"])
            .spawn();

        std::process::exit(0);
    }

    #[cfg(not(windows))]
    Ok(())
}

#[derive(Clone, Default, Serialize)]
pub struct UpdateCheckResult {
    pub available: bool,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub version: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub date: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub body: Option<String>,
}

#[derive(Default, Serialize, Deserialize)]
struct PersistedUpdateState {
    last_check: i64,
    app_version: String,
    version: String,
    date: String,
    /// Version whose installer was last launched, auto or manual.
    #[serde(default)]
    attempted_version: String,
    /// Unix seconds of the last install attempt.
    #[serde(default)]
    attempted_at: i64,
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
    }
}

/// Persisted state only describes the version that wrote it: after an update it
/// still names the old version and the update it found, which must never be
/// shown against the newly installed one.
fn state_is_current(state: &PersistedUpdateState, current_version: &str) -> bool {
    state.app_version == current_version
}

/// Returns a cached result only when the last check is recent and was made
/// against the version currently running (so updating invalidates the cache).
fn cached_result(app: &AppHandle) -> Option<UpdateCheckResult> {
    let state = read_state(app);
    if state.last_check == 0 || now_secs() - state.last_check >= CHECK_INTERVAL_SECS {
        return None;
    }
    if !state_is_current(&state, &app.package_info().version.to_string()) {
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

/// True when an installer was already launched for this exact version recently.
/// On Windows the process exits from inside the install path, so without this
/// a failed install would relaunch the installer on every startup.
fn install_attempted_recently(state: &PersistedUpdateState, version: &str, now: i64) -> bool {
    state.attempted_version == version
        && state.attempted_at > 0
        && now - state.attempted_at < AUTO_INSTALL_RETRY_SECS
}

/// Checks for an update at most once per `CHECK_INTERVAL_SECS` unless `force`
/// is set. Development builds skip the automatic network check because their
/// version never tracks releases; manual checks still work.
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

    let tauri_check_result = match app.updater() {
        Ok(updater) => {
            match tokio::time::timeout(Duration::from_secs(CHECK_TIMEOUT_SECS), updater.check()).await {
                Ok(Ok(Some(update))) => Some(UpdateCheckResult {
                    available: true,
                    version: Some(update.version.clone()),
                    date: update
                        .raw_json
                        .get("pub_date")
                        .and_then(|value| value.as_str())
                        .map(str::to_string),
                    body: update.body.clone(),
                }),
                Ok(Ok(None)) => Some(UpdateCheckResult::default()),
                _ => None,
            }
        }
        Err(_) => None,
    };

    let result = if let Some(res) = tauri_check_result {
        res
    } else {
        match check_github_releases(&app.package_info().version.to_string()).await {
            Some((tag, date, body, download_url)) => {
                if let Ok(mut g_url) = GITHUB_DOWNLOAD_URL.lock() {
                    *g_url = download_url;
                }
                UpdateCheckResult {
                    available: true,
                    version: Some(tag),
                    date,
                    body,
                }
            }
            None => UpdateCheckResult::default(),
        }
    };

    let previous = read_state(app);
    write_state(
        app,
        &PersistedUpdateState {
            last_check: now_secs(),
            app_version: app.package_info().version.to_string(),
            version: result.version.clone().unwrap_or_default(),
            date: result.date.clone().unwrap_or_default(),
            attempted_version: previous.attempted_version,
            attempted_at: previous.attempted_at,
        },
    );
    if let Ok(mut cached) = LAST_CHECK.lock() {
        *cached = Some(result.clone());
    }
    // Report the outcome (including "no update") so the UI can clear a stale
    // badge after this version replaced one that had an update pending.
    let _ = app.emit("update-available", &result);
    Ok(result)
}

/// Downloads and installs the available update. On Windows the updater plugin
/// launches the NSIS installer and terminates this process, which then
/// relaunches the app; on other platforms this returns after restarting.
pub async fn install(app: &AppHandle) -> Result<(), String> {
    if UPDATE_BUSY.swap(true, Ordering::SeqCst) {
        return Err("an update is already in progress".to_string());
    }
    let result = install_inner(app).await;
    UPDATE_BUSY.store(false, Ordering::SeqCst);
    result
}

async fn install_inner(app: &AppHandle) -> Result<(), String> {
    // Hold the check lock so a concurrent check cannot mutate state mid-install.
    let _guard = CHECK_LOCK.lock().await;

    let tauri_attempt = async {
        let hook_handle = app.clone();
        let updater = app
            .updater_builder()
            .on_before_exit(move || {
                crate::state::SHUTTING_DOWN.store(true, Ordering::Relaxed);
                crate::commands::release_appbars(&hook_handle);
                crate::utils::set_taskbar_visibility(true, true);
                crate::services::set_native_osd_suppressed(false);
                let _ = hook_handle.emit(
                    "auto-update-status",
                    serde_json::json!({ "status": "installing" }),
                );
                hook_handle.cleanup_before_exit();
            })
            .build()
            .map_err(|e| e.to_string())?;

        let update = updater
            .check()
            .await
            .map_err(|e| e.to_string())?
            .ok_or_else(|| "no update available".to_string())?;

        let _ = app.emit(
            "auto-update-status",
            serde_json::json!({ "status": "downloading", "progress": 0 }),
        );

        let progress_handle = app.clone();
        let downloaded = Arc::new(AtomicU64::new(0));
        let downloaded_cb = downloaded.clone();
        let bytes = update
            .download(
                move |chunk_len, total| {
                    let current =
                        downloaded_cb.fetch_add(chunk_len as u64, Ordering::Relaxed) + chunk_len as u64;
                    if let Some(total) = total {
                        if total > 0 {
                            let progress = (current.saturating_mul(100) / total) as u32;
                            let _ = progress_handle.emit(
                                "auto-update-status",
                                serde_json::json!({ "status": "downloading", "progress": progress }),
                            );
                        }
                    }
                },
                || {},
            )
            .await
            .map_err(|e| e.to_string())?;

        {
            let mut state = read_state(app);
            state.attempted_version = update.version.clone();
            state.attempted_at = now_secs();
            write_state(app, &state);
        }

        update.install(bytes).map_err(|e| e.to_string())?;

        #[cfg(not(windows))]
        {
            let _ = app.emit(
                "auto-update-status",
                serde_json::json!({ "status": "done" }),
            );
            app.restart()
        }

        #[cfg(windows)]
        Ok::<(), String>(())
    }.await;

    if tauri_attempt.is_ok() {
        return Ok(());
    }

    let download_url = {
        let g_url = GITHUB_DOWNLOAD_URL.lock().ok().and_then(|g| g.clone());
        g_url.ok_or_else(|| "no update download URL available".to_string())?
    };

    install_from_url(app, &download_url).await
}

/// Startup entry point: always checks so the UI can show an update badge, and
/// auto-installs only when the user enabled it and the release has aged.
pub async fn run_startup_check(app: AppHandle) {
    let auto_update =
        crate::utils::get_setting_str(&app, "gloom-auto-update").as_deref() == Some("true");

    if auto_update {
        let _ = app.emit(
            "auto-update-status",
            serde_json::json!({ "status": "checking" }),
        );
    }

    let result = match check(&app, false).await {
        Ok(result) => result,
        Err(_) => {
            if auto_update {
                let _ = app.emit(
                    "auto-update-status",
                    serde_json::json!({ "status": "done" }),
                );
            }
            return;
        }
    };

    if !result.available {
        if auto_update {
            let _ = app.emit(
                "auto-update-status",
                serde_json::json!({ "status": "done" }),
            );
        }
        return;
    }

    let attempted_version = result.version.clone().unwrap_or_default();
    let attempted_recently =
        install_attempted_recently(&read_state(&app), &attempted_version, now_secs());

    if auto_update && release_is_old_enough(&result) && !attempted_recently {
        // On Windows this never returns: the installer exits the process.
        if install(&app).await.is_err() {
            let _ = app.emit(
                "auto-update-status",
                serde_json::json!({ "status": "done" }),
            );
        }
    } else if auto_update {
        // An install for this version was already attempted (or is pending),
        // so keep the badge visible instead of relaunching the installer on
        // every startup.
        let _ = app.emit(
            "auto-update-status",
            serde_json::json!({ "status": "done" }),
        );
    }
}

#[tauri::command]
pub async fn check_for_updates(app: AppHandle, force: bool) -> Result<UpdateCheckResult, String> {
    // `check` emits the result (available or not) so callers don't have to.
    check(&app, force).await
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
    let state = read_state(&app);
    if !state_is_current(&state, &app.package_info().version.to_string()) {
        return UpdateCheckResult::default();
    }
    result_from_state(&state)
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
    if !(1..=12).contains(&month)
        || !(1..=31).contains(&day)
        || hour > 23
        || minute > 59
        || second > 60
    {
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
    use super::{
        days_from_civil, install_attempted_recently, parse_rfc3339_utc, state_is_current,
        PersistedUpdateState,
    };

    #[test]
    fn parses_tauri_action_pub_date() {
        assert_eq!(parse_rfc3339_utc("1970-01-01T00:00:00Z"), Some(0));
        assert_eq!(
            parse_rfc3339_utc("2026-09-13T18:14:46Z"),
            Some(1_789_323_286)
        );
        assert_eq!(
            parse_rfc3339_utc("2026-09-13T18:14:46.123Z"),
            Some(1_789_323_286)
        );
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
    fn parses_legacy_update_state_without_attempt_fields() {
        let state: PersistedUpdateState = serde_json::from_str(
            r#"{"last_check":1,"app_version":"3.8.7","version":"3.8.8","date":"2026-09-13T18:14:46Z"}"#,
        )
        .expect("legacy state parses");
        assert_eq!(state.attempted_version, "");
        assert_eq!(state.attempted_at, 0);
    }

    #[test]
    fn skips_recent_install_attempts_only() {
        let state = PersistedUpdateState {
            attempted_version: "3.8.8".into(),
            attempted_at: 1_000,
            ..Default::default()
        };

        assert!(install_attempted_recently(&state, "3.8.8", 1_000 + 3_600));
        assert!(!install_attempted_recently(
            &state,
            "3.8.8",
            1_000 + 24 * 60 * 60
        ));
        assert!(!install_attempted_recently(&state, "3.8.9", 1_000 + 3_600));

        let empty = PersistedUpdateState::default();
        assert!(!install_attempted_recently(&empty, "3.8.8", 1_000));
    }

    #[test]
    fn persisted_state_only_applies_to_the_version_that_wrote_it() {
        // State written by 3.8.7 saying "3.9.1 is available" must not surface
        // once 3.9.1 is installed, or the badge sticks for a whole session.
        let state = PersistedUpdateState {
            app_version: "3.8.7".into(),
            version: "3.9.1".into(),
            ..Default::default()
        };

        assert!(state_is_current(&state, "3.8.7"));
        assert!(!state_is_current(&state, "3.9.1"));
    }
}
