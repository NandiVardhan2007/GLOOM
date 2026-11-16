//! Linux Shelf Preview implementation for GLOOM.

use std::fs;
use std::path::Path;

#[derive(Clone, serde::Serialize)]
pub struct ShelfPreview {
    pub thumb: Option<String>,
    pub text: Option<String>,
    pub folder: bool,
}

pub fn preview(path: &str) -> ShelfPreview {
    let p = Path::new(path);
    let is_dir = p.is_dir();
    let text = if !is_dir {
        fs::read_to_string(p)
            .ok()
            .map(|s| s.chars().take(400).collect())
    } else {
        None
    };
    ShelfPreview {
        thumb: None,
        text,
        folder: is_dir,
    }
}
