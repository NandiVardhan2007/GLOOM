//! Linux Wi-Fi implementation for GLOOM using NetworkManager / nmcli.

use serde::Serialize;
use std::process::Command;
use tauri::AppHandle;

#[derive(Clone, Serialize)]
pub struct WifiNetwork {
    pub ssid: String,
    pub signal: u32,
    pub security: &'static str,
    pub connected: bool,
    pub saved: bool,
}

#[derive(Clone, Serialize)]
pub struct WifiNetworkList {
    pub networks: Vec<WifiNetwork>,
    pub location_denied: bool,
}

pub fn setup_wifi_notifications(_app: AppHandle) {
    // Background watcher can be expanded with DBus NetworkManager signal listener
}

pub fn connected_ssid() -> Option<String> {
    let output = Command::new("nmcli")
        .args(["-t", "-f", "ACTIVE,SSID", "dev", "wifi"])
        .output()
        .ok()?;
    if !output.status.success() {
        return None;
    }
    let stdout = String::from_utf8_lossy(&output.stdout);
    for line in stdout.lines() {
        if line.starts_with("yes:") {
            let ssid = line.trim_start_matches("yes:").trim().to_string();
            if !ssid.is_empty() {
                return Some(ssid);
            }
        }
    }
    None
}

#[tauri::command]
pub async fn wifi_list_networks() -> Result<WifiNetworkList, String> {
    let output = Command::new("nmcli")
        .args(["-t", "-f", "IN-USE,SSID,SIGNAL,SECURITY", "dev", "wifi"])
        .output()
        .map_err(|e| format!("Failed to execute nmcli: {}", e))?;

    let mut networks = Vec::new();
    let stdout = String::from_utf8_lossy(&output.stdout);

    for line in stdout.lines() {
        let parts: Vec<&str> = line.split(':').collect();
        if parts.len() >= 4 {
            let in_use = parts[0].trim() == "*";
            let ssid = parts[1].trim().to_string();
            if ssid.is_empty() {
                continue;
            }
            let signal = parts[2].trim().parse::<u32>().unwrap_or(50);
            let sec_str = parts[3].to_lowercase();
            let security = if sec_str.contains("wpa3") {
                "wpa3"
            } else if sec_str.contains("wpa2") || sec_str.contains("wpa") {
                "wpa2"
            } else if sec_str.contains("wep") {
                "wep"
            } else if sec_str.is_empty() || sec_str.contains("--") {
                "open"
            } else {
                "other"
            };

            networks.push(WifiNetwork {
                ssid,
                signal,
                security,
                connected: in_use,
                saved: in_use,
            });
        }
    }

    Ok(WifiNetworkList {
        networks,
        location_denied: false,
    })
}

#[tauri::command]
pub async fn wifi_scan() -> Result<(), String> {
    let _ = Command::new("nmcli").args(["dev", "wifi", "rescan"]).spawn();
    Ok(())
}

#[tauri::command]
pub async fn wifi_connect(ssid: String, password: Option<String>) -> Result<(), String> {
    let mut cmd = Command::new("nmcli");
    cmd.args(["dev", "wifi", "connect", &ssid]);
    if let Some(pwd) = password {
        cmd.args(["password", &pwd]);
    }
    let status = cmd.status().map_err(|e| e.to_string())?;
    if status.success() {
        Ok(())
    } else {
        Err(format!("nmcli failed with status: {}", status))
    }
}

#[tauri::command]
pub async fn wifi_disconnect() -> Result<(), String> {
    // Disconnect active Wi-Fi device
    let _ = Command::new("nmcli")
        .args(["dev", "disconnect", "wlan0"])
        .status();
    Ok(())
}

#[tauri::command]
pub async fn wifi_forget(ssid: String) -> Result<(), String> {
    let _ = Command::new("nmcli")
        .args(["connection", "delete", &ssid])
        .status();
    Ok(())
}

#[tauri::command]
pub fn wifi_open_settings(_page: String) -> Result<(), String> {
    let _ = Command::new("gnome-control-center")
        .arg("wifi")
        .spawn()
        .or_else(|_| Command::new("xdg-open").arg("settings://wifi").spawn());
    Ok(())
}
