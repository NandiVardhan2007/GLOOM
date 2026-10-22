//! Desktop notifications for the island.
//!
//! Two sources feed the same island activity:
//!  * Windows toasts, read through the public UserNotificationListener API with
//!    the user's permission. Nothing is read until Windows grants access.
//!  * Notifications JARVIS raises itself (a finished transfer, an AI reply).
//!
//! No notification content is written to disk or sent over the bridge.

use serde::Serialize;
use tauri::{AppHandle, Emitter};

#[derive(Debug, Clone, Serialize)]
pub struct DesktopNotification {
    pub app_name: String,
    pub title: String,
    pub text: String,
    pub otp: Option<String>,
    pub source: String,
    pub timestamp_ms: u64,
}

pub fn push(app: &AppHandle, app_name: &str, title: &str, text: &str, otp: Option<&str>, source: &str) {
    let payload = DesktopNotification {
        app_name: app_name.to_string(),
        title: title.to_string(),
        text: text.to_string(),
        otp: otp.map(|value| value.to_string()),
        source: source.to_string(),
        timestamp_ms: std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .map(|d| d.as_millis() as u64)
            .unwrap_or(0),
    };
    let _ = app.emit("jarvis://notification", payload);
}

/// Windows toast listener. Polls the alert list because the WinRT change event
/// needs a COM callback that outlives the poll loop; polling is simpler and
/// costs one call every 1.5 seconds.
#[cfg(windows)]
pub fn start_windows_listener(app: AppHandle) {
    std::thread::spawn(move || {
        use std::collections::HashSet;
        use std::time::Duration;
        use windows::UI::Notifications::Management::{
            UserNotificationListener, UserNotificationListenerAccessStatus,
        };
        use windows::UI::Notifications::{NotificationKinds, UserNotification};
        use windows::Win32::System::WinRT::{RoInitialize, RO_INIT_MULTITHREADED};

        let _ = unsafe { RoInitialize(RO_INIT_MULTITHREADED) };

        let listener = match UserNotificationListener::Current() {
            Ok(listener) => listener,
            Err(error) => {
                log::warn!("Notification listener unavailable: {error}");
                return;
            }
        };

        match wait_async_5s(|| listener.RequestAccessAsync()) {
            Some(Ok(status)) if status == UserNotificationListenerAccessStatus::Allowed => {}
            Some(Ok(status)) => {
                log::info!("Notification access not granted ({status:?}); desktop toasts stay off");
                return;
            }
            Some(Err(error)) => {
                log::info!("Notification access request failed: {error}");
                return;
            }
            None => {
                log::info!("Notification access request timed out");
                return;
            }
        }

        let mut seen: HashSet<u32> = HashSet::new();
        let mut first_pass = true;

        loop {
            std::thread::sleep(Duration::from_millis(1_500));
            let notifications = match wait_async_5s(|| {
                listener.GetNotificationsAsync(NotificationKinds::Toast)
            }) {
                Some(Ok(list)) => list,
                Some(Err(error)) => {
                    log::debug!("Notification poll failed: {error}");
                    continue;
                }
                None => {
                    log::debug!("Notification poll timed out");
                    continue;
                }
            };

            let count = notifications.Size().unwrap_or(0);
            for index in 0..count {
                let user_notification: UserNotification = match notifications.GetAt(index) {
                    Ok(value) => value,
                    Err(_) => continue,
                };
                let id = match user_notification.Id() {
                    Ok(value) => value,
                    Err(_) => continue,
                };
                if !seen.insert(id) {
                    continue;
                }
                if first_pass {
                    continue;
                }
                if let Some((app_name, title, text)) = extract(&user_notification) {
                    let otp = extract_otp(&format!("{title} {text}"));
                    push(&app, &app_name, &title, &text, otp.as_deref(), "windows");
                }
            }
            if seen.len() > 400 {
                seen.clear();
            }
            first_pass = false;
        }
    });
}

/// Waits for a WinRT async operation without pulling in an async runtime.
/// The operation is polled on this thread, so no COM value crosses threads.
#[cfg(windows)]
fn wait_async_5s<T, F>(start: F) -> Option<windows::core::Result<T>>
where
    T: windows::core::RuntimeType + 'static,
    F: FnOnce() -> windows::core::Result<windows_future::IAsyncOperation<T>>,
{
    use std::time::{Duration, Instant};
    use windows_future::AsyncStatus;

    let operation = start().ok()?;
    let deadline = Instant::now() + Duration::from_secs(5);
    loop {
        match operation.Status() {
            Ok(AsyncStatus::Completed) => return Some(operation.GetResults()),
            Ok(AsyncStatus::Canceled) | Ok(AsyncStatus::Error) => return None,
            Ok(_) => {}
            Err(_) => return None,
        }
        if Instant::now() >= deadline {
            return None;
        }
        std::thread::sleep(Duration::from_millis(40));
    }
}

#[cfg(windows)]
fn extract(
    notification: &windows::UI::Notifications::UserNotification,
) -> Option<(String, String, String)> {
    use windows::UI::Notifications::{KnownNotificationBindings, Notification};

    let raw: Notification = notification.Notification().ok()?;
    let visual = raw.Visual().ok()?;
    let generic = KnownNotificationBindings::ToastGeneric().ok()?;
    let binding = visual.GetBinding(&generic).ok()?;
    let texts = binding.GetTextElements().ok()?;
    let count = texts.Size().unwrap_or(0);
    let mut parts: Vec<String> = Vec::new();
    for index in 0..count {
        if let Ok(element) = texts.GetAt(index) {
            if let Ok(text) = element.Text() {
                let value = text.to_string();
                if !value.trim().is_empty() {
                    parts.push(value);
                }
            }
        }
    }
    if parts.is_empty() {
        return None;
    }

    let app_name = notification
        .AppInfo()
        .ok()
        .and_then(|info| info.DisplayInfo().ok())
        .and_then(|display| display.DisplayName().ok())
        .map(|name| name.to_string())
        .filter(|name| !name.trim().is_empty())
        .unwrap_or_else(|| "Desktop".to_string());

    let title = parts.first().cloned().unwrap_or_default();
    let text = parts
        .iter()
        .skip(1)
        .cloned()
        .collect::<Vec<_>>()
        .join(" ");
    Some((app_name, title, text))
}

#[cfg(not(windows))]
pub fn start_windows_listener(_app: AppHandle) {
    // Other platforms keep the self-raised notification path.
}

/// Finds a one-time code near a word that says it is one. Kept local so the
/// desktop side never needs to send notification text anywhere.
pub fn extract_otp(text: &str) -> Option<String> {
    const CONTEXT: [&str; 10] = [
        "code", "otp", "one-time", "one time", "verification", "verify",
        "passcode", "pin", "login", "security",
    ];
    let lower = text.to_lowercase();
    let chars: Vec<char> = lower.chars().collect();
    let mut index = 0;
    while index < chars.len() {
        if !chars[index].is_ascii_digit() {
            index += 1;
            continue;
        }
        let start = index;
        let mut digits = String::new();
        let mut cursor = index;
        while cursor < chars.len() {
            let ch = chars[cursor];
            if ch.is_ascii_digit() {
                digits.push(ch);
                cursor += 1;
            } else if (ch == ' ' || ch == '-')
                && cursor + 1 < chars.len()
                && chars[cursor + 1].is_ascii_digit()
                && digits.len() < 8
            {
                cursor += 1;
            } else {
                break;
            }
        }
        if (4..=8).contains(&digits.len()) {
            let from = start.saturating_sub(28);
            let to = (cursor + 28).min(chars.len());
            let before: String = chars[from..start].iter().collect();
            let after: String = chars[cursor..to].iter().collect();
            let nearby = CONTEXT
                .iter()
                .any(|word| before.contains(word) || after.contains(word));
            let looks_like_year = digits.len() == 4
                && digits
                    .parse::<u32>()
                    .map(|value| (1900..=2100).contains(&value))
                    .unwrap_or(false);
            if nearby && !looks_like_year {
                return Some(digits);
            }
        }
        index = cursor.max(start + 1);
    }
    None
}
