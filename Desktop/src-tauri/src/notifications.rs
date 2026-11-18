use serde::{Deserialize, Serialize};
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Mutex;
use std::time::{SystemTime, UNIX_EPOCH};
use tauri::{AppHandle, Emitter};

#[derive(Clone, Serialize, Deserialize, Debug, PartialEq)]
pub struct ToastNotification {
    pub id: u32,
    pub app_name: String,
    pub title: String,
    pub body: String,
    pub timestamp: u64,
}

static NOTIFICATION_LISTENER_ACTIVE: AtomicBool = AtomicBool::new(false);
static LAST_SEEN_NOTIFICATION_ID: Mutex<Option<u32>> = Mutex::new(None);

pub fn now_secs() -> u64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_secs())
        .unwrap_or(0)
}

#[cfg(windows)]
pub fn start_notification_listener(app_handle: AppHandle) {
    if NOTIFICATION_LISTENER_ACTIVE.swap(true, Ordering::SeqCst) {
        return;
    }

    std::thread::spawn(move || {
        use windows::UI::Notifications::Management::{
            UserNotificationListener, UserNotificationListenerAccessStatus,
        };
        use windows::UI::Notifications::{KnownNotificationBindings, NotificationKinds};

        let Ok(listener) = UserNotificationListener::Current() else {
            crate::diagnostics::log("notifications: UserNotificationListener not supported on this OS");
            return;
        };

        let access = match listener.RequestAccessAsync() {
            Ok(op) => match op.get() {
                Ok(status) => status,
                Err(e) => {
                    crate::diagnostics::log(&format!("notifications: RequestAccess error: {e}"));
                    return;
                }
            },
            Err(e) => {
                crate::diagnostics::log(&format!("notifications: failed to request access: {e}"));
                return;
            }
        };

        if access != UserNotificationListenerAccessStatus::Allowed {
            crate::diagnostics::log(&format!("notifications: access not allowed ({access:?})"));
            return;
        }

        crate::diagnostics::log("notifications: access granted, listening for toast notifications");

        loop {
            std::thread::sleep(std::time::Duration::from_millis(1000));

            if crate::state::SHUTTING_DOWN.load(Ordering::Relaxed) {
                break;
            }

            let Ok(op) = listener.GetNotificationsAsync(NotificationKinds::Toast) else {
                continue;
            };

            let Ok(notifications) = op.get() else {
                continue;
            };

            let Ok(size) = notifications.Size() else {
                continue;
            };

            if size == 0 {
                continue;
            }

            if let Ok(latest) = notifications.GetAt(size - 1) {
                let Ok(id) = latest.Id() else {
                    continue;
                };

                let mut last_id_guard = match LAST_SEEN_NOTIFICATION_ID.lock() {
                    Ok(g) => g,
                    Err(_) => continue,
                };

                if last_id_guard.is_none() {
                    *last_id_guard = Some(id);
                    continue;
                }

                if *last_id_guard == Some(id) {
                    continue;
                }

                *last_id_guard = Some(id);
                drop(last_id_guard);

                let app_name = latest
                    .AppInfo()
                    .and_then(|info| info.DisplayInfo())
                    .and_then(|disp| disp.DisplayName())
                    .map(|h| h.to_string())
                    .unwrap_or_else(|_| "Notification".to_string());

                let mut title = String::new();
                let mut body = String::new();

                if let Ok(notification) = latest.Notification() {
                    if let Ok(visual) = notification.Visual() {
                        if let Ok(toast_generic) = KnownNotificationBindings::ToastGeneric() {
                            if let Ok(binding) = visual.GetBinding(&toast_generic) {
                                if let Ok(text_elements) = binding.GetTextElements() {
                                    if let Ok(text_count) = text_elements.Size() {
                                        if text_count > 0 {
                                            if let Ok(t0) = text_elements.GetAt(0) {
                                                if let Ok(text) = t0.Text() {
                                                    title = text.to_string();
                                                }
                                            }
                                        }
                                        if text_count > 1 {
                                            if let Ok(t1) = text_elements.GetAt(1) {
                                                if let Ok(text) = t1.Text() {
                                                    body = text.to_string();
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if title.is_empty() && body.is_empty() {
                    continue;
                }

                let toast = ToastNotification {
                    id,
                    app_name,
                    title,
                    body,
                    timestamp: now_secs(),
                };

                let _ = app_handle.emit("windows-toast-notification", &toast);
            }
        }
    });
}

#[cfg(not(windows))]
pub fn start_notification_listener(app_handle: AppHandle) {
    let _ = app_handle;
}

#[tauri::command]
pub fn get_notification_listener_status() -> bool {
    NOTIFICATION_LISTENER_ACTIVE.load(Ordering::Relaxed)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_toast_notification_serialization() {
        let toast = ToastNotification {
            id: 42,
            app_name: "Slack".to_string(),
            title: "New Message".to_string(),
            body: "Hey there!".to_string(),
            timestamp: 1700000000,
        };

        let json = serde_json::to_string(&toast).expect("serialize");
        assert!(json.contains("\"app_name\":\"Slack\""));
        assert!(json.contains("\"title\":\"New Message\""));

        let deserialized: ToastNotification = serde_json::from_str(&json).expect("deserialize");
        assert_eq!(toast, deserialized);
    }
}
