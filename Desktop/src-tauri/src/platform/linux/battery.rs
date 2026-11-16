//! Linux Battery implementation for GLOOM.
//! Reads power supply status, capacity, and rate from /sys/class/power_supply.

use serde::Serialize;
use std::fs;
use std::path::Path;

#[derive(Clone, Serialize, Default, Debug, PartialEq)]
pub struct BatteryDetails {
    pub present: bool,
    pub percent: Option<u8>,
    pub plugged_in: bool,
    pub charging: bool,
    pub seconds_left: Option<u64>,
    pub seconds_to_full: Option<u64>,
    pub health_percent: Option<u8>,
    pub power_mode: Option<&'static str>,
}

#[tauri::command]
pub async fn get_battery_details() -> Result<BatteryDetails, String> {
    let mut details = BatteryDetails::default();
    let base = Path::new("/sys/class/power_supply");
    if !base.exists() {
        details.plugged_in = true;
        details.power_mode = Some("Balanced");
        return Ok(details);
    }

    let mut found_battery = false;
    if let Ok(entries) = fs::read_dir(base) {
        for entry in entries.flatten() {
            let name = entry.file_name().to_string_lossy().to_string();
            let path = entry.path();
            let p_type = fs::read_to_string(path.join("type"))
                .unwrap_or_default()
                .trim()
                .to_lowercase();

            if p_type == "battery" || name.starts_with("BAT") {
                found_battery = true;
                details.present = true;

                if let Ok(cap) = fs::read_to_string(path.join("capacity")) {
                    if let Ok(pct) = cap.trim().parse::<u8>() {
                        details.percent = Some(pct);
                    }
                }

                if let Ok(status) = fs::read_to_string(path.join("status")) {
                    let s = status.trim().to_lowercase();
                    details.charging = s == "charging";
                    details.plugged_in = s == "charging" || s == "full" || s == "not charging";
                }

                let energy_now = fs::read_to_string(path.join("energy_now"))
                    .or_else(|_| fs::read_to_string(path.join("charge_now")))
                    .ok()
                    .and_then(|v| v.trim().parse::<u64>().ok());
                let energy_full = fs::read_to_string(path.join("energy_full"))
                    .or_else(|_| fs::read_to_string(path.join("charge_full")))
                    .ok()
                    .and_then(|v| v.trim().parse::<u64>().ok());
                let power_now = fs::read_to_string(path.join("power_now"))
                    .or_else(|_| fs::read_to_string(path.join("current_now")))
                    .ok()
                    .and_then(|v| v.trim().parse::<u64>().ok());

                if let (Some(now), Some(full), Some(power)) = (energy_now, energy_full, power_now) {
                    if power > 0 {
                        if details.charging {
                            if full > now {
                                details.seconds_to_full = Some(((full - now) * 3600) / power);
                            }
                        } else {
                            details.seconds_left = Some((now * 3600) / power);
                        }
                    }
                }

                let full_design = fs::read_to_string(path.join("energy_full_design"))
                    .or_else(|_| fs::read_to_string(path.join("charge_full_design")))
                    .ok()
                    .and_then(|v| v.trim().parse::<u64>().ok());
                if let (Some(full), Some(design)) = (energy_full, full_design) {
                    if design > 0 {
                        let health = ((full as f64 / design as f64) * 100.0).round() as u8;
                        details.health_percent = Some(health.min(100));
                    }
                }
                break;
            } else if p_type == "mains" || name.starts_with("AC") {
                if let Ok(online) = fs::read_to_string(path.join("online")) {
                    if online.trim() == "1" {
                        details.plugged_in = true;
                    }
                }
            }
        }
    }

    if !found_battery {
        details.plugged_in = true;
    }

    details.power_mode = Some("Balanced");
    Ok(details)
}
