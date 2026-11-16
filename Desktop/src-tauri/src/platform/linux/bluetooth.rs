//! Linux Bluetooth implementation for GLOOM using bluetoothctl / BlueZ.

use serde::Serialize;
use std::process::Command;
use tauri::AppHandle;

#[derive(Clone, Serialize)]
pub struct BtDevice {
    pub id: String,
    pub name: String,
    pub kind: &'static str,
    pub connected: bool,
    pub battery: Option<u8>,
    pub can_connect: bool,
}

pub fn setup_bluetooth_notifications(_app: AppHandle) {}

#[tauri::command]
pub async fn bluetooth_list_devices() -> Result<Vec<BtDevice>, String> {
    let output = Command::new("bluetoothctl")
        .arg("devices")
        .output()
        .map_err(|e| e.to_string())?;

    let stdout = String::from_utf8_lossy(&output.stdout);
    let mut devices = Vec::new();

    for line in stdout.lines() {
        let parts: Vec<&str> = line.split_whitespace().collect();
        if parts.len() >= 3 && parts[0] == "Device" {
            let id = parts[1].to_string();
            let name = parts[2..].join(" ");
            let lower_name = name.to_lowercase();

            let kind = if lower_name.contains("headset")
                || lower_name.contains("headphones")
                || lower_name.contains("airpods")
                || lower_name.contains("buds")
                || lower_name.contains("audio")
            {
                "audio"
            } else if lower_name.contains("mouse") {
                "mouse"
            } else if lower_name.contains("keyboard") {
                "keyboard"
            } else if lower_name.contains("phone") {
                "phone"
            } else {
                "other"
            };

            devices.push(BtDevice {
                id,
                name,
                kind,
                connected: false,
                battery: None,
                can_connect: kind == "audio",
            });
        }
    }

    Ok(devices)
}

#[tauri::command]
pub async fn bluetooth_set_connected(id: String, connected: bool) -> Result<(), String> {
    let action = if connected { "connect" } else { "disconnect" };
    let status = Command::new("bluetoothctl")
        .args([action, &id])
        .status()
        .map_err(|e| e.to_string())?;
    if status.success() {
        Ok(())
    } else {
        Err(format!("bluetoothctl {} failed", action))
    }
}

#[tauri::command]
pub async fn bluetooth_discovery(active: bool) -> Result<(), String> {
    let action = if active { "on" } else { "off" };
    let _ = Command::new("bluetoothctl")
        .args(["scan", action])
        .spawn();
    Ok(())
}

#[tauri::command]
pub async fn bluetooth_pair(id: String) -> Result<(), String> {
    let status = Command::new("bluetoothctl")
        .args(["pair", &id])
        .status()
        .map_err(|e| e.to_string())?;
    if status.success() {
        Ok(())
    } else {
        Err("Failed to pair bluetooth device".to_string())
    }
}

#[tauri::command]
pub fn bluetooth_pairing_respond(_accept: bool, _pin: Option<String>) {}

#[tauri::command]
pub async fn bluetooth_unpair(id: String) -> Result<(), String> {
    let status = Command::new("bluetoothctl")
        .args(["remove", &id])
        .status()
        .map_err(|e| e.to_string())?;
    if status.success() {
        Ok(())
    } else {
        Err("Failed to remove bluetooth device".to_string())
    }
}
