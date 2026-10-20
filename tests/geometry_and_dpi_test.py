"""
Automated Test Suite: Geometry, Coordinate Calculations & DPI Scaling
Project jarvis
"""

import unittest
import math

class TestJARVISGeometry(unittest.TestCase):
    def calculate_centered_bounds(self, screen_left: int, screen_width: int, width_dip: float, height_dip: float, dpi_scale: float):
        """Calculates physical pixel bounds centered horizontally on the monitor."""
        width_px = int(math.ceil(width_dip * dpi_scale))
        height_px = int(math.ceil(height_dip * dpi_scale))
        x_px = screen_left + int(math.floor((screen_width - width_px) / 2.0))
        return {
            "x": x_px,
            "width": width_px,
            "height": height_px,
        }

    def test_horizontal_centering_1080p_100dpi(self):
        # 1920x1080 @ 100% scale
        res = self.calculate_centered_bounds(0, 1920, 210, 34, 1.0)
        self.assertEqual(res["width"], 210)
        self.assertEqual(res["height"], 34)
        # Center should be at (1920 - 210) / 2 = 855
        self.assertEqual(res["x"], 855)

    def test_horizontal_centering_1440p_125dpi(self):
        # 2560x1440 @ 125% scale
        res = self.calculate_centered_bounds(0, 2560, 210, 34, 1.25)
        # Width: ceil(210 * 1.25) = 263
        self.assertEqual(res["width"], 263)
        # Center should be at (2560 - 263) / 2 = 1148
        self.assertEqual(res["x"], 1148)

    def test_horizontal_centering_4k_150dpi(self):
        # 3840x2160 @ 150% scale
        res = self.calculate_centered_bounds(0, 3840, 560, 220, 1.5)
        self.assertEqual(res["width"], 840)
        self.assertEqual(res["height"], 330)
        # Center: (3840 - 840) / 2 = 1500
        self.assertEqual(res["x"], 1500)

    def test_secondary_monitor_offset(self):
        # Secondary monitor to the left at x = -1920, width = 1920
        res = self.calculate_centered_bounds(-1920, 1920, 210, 34, 1.0)
        self.assertEqual(res["width"], 210)
        # x = -1920 + 855 = -1065
        self.assertEqual(res["x"], -1065)

    def test_squircle_curvature_clamping(self):
        """Verifies squircle corner radii do not exceed geometric limits."""
        width = 210.0
        height = 34.0
        top_flare = 10.0
        requested_bottom_radius = 24.0

        clamped_br = min(requested_bottom_radius, height, (width - 2 * top_flare) / 2.0)
        # Should be clamped to height (34.0) or half available width (95.0) -> 24.0 is safe
        self.assertEqual(clamped_br, 24.0)

        # Extreme narrow width
        narrow_width = 30.0
        clamped_narrow = min(requested_bottom_radius, height, (narrow_width - 2 * top_flare) / 2.0)
        # (30 - 20) / 2 = 5.0
        self.assertEqual(clamped_narrow, 5.0)

if __name__ == "__main__":
    unittest.main()
