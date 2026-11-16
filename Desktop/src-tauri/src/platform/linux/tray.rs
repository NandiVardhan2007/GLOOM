//! Linux Tray apps integration for GLOOM.

use serde::{Deserialize, Serialize};

#[derive(Clone, Serialize, Deserialize)]
pub struct TrayApp {
    pub name: String,
    pub path: String,
    pub tray_ids: Vec<String>,
    pub window_handles: Vec<isize>,
}

pub fn enumerate() -> Vec<TrayApp> {
    Vec::new()
}

pub fn interact(_key: &str, _context_menu: bool) -> Result<(), String> {
    Ok(())
}
