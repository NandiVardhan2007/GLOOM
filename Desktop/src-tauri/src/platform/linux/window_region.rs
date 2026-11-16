//! Linux Window Region implementation for GLOOM.

use tauri::Window;
use crate::types::IntRect;
use gtk::prelude::*;

#[tauri::command]
pub fn set_window_region(window: Window, rects: Vec<IntRect>) {
    if let Ok(gtk_win) = window.gtk_window() {
        let scale = window.scale_factor().unwrap_or(1.0);
        let cairo_rects: Vec<cairo::RectangleInt> = rects
            .iter()
            .filter(|r| r.width > 0 && r.height > 0)
            .map(|r| {
                cairo::RectangleInt::new(
                    (r.x as f64 * scale).floor() as i32,
                    (r.y as f64 * scale).floor() as i32,
                    (r.width as f64 * scale).ceil() as i32,
                    (r.height as f64 * scale).ceil() as i32,
                )
            })
            .collect();

        let region = if cairo_rects.is_empty() {
            cairo::Region::create()
        } else {
            cairo::Region::create_rectangles(&cairo_rects)
        };

        gtk_win.input_shape_combine_region(Some(&region));
    }
}
