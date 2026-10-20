"""
Automated Test Suite: Media Payload, Time Formatting & Artwork Handling
Project jarvis
"""

import unittest
import base64

def format_time_ms(ms: int) -> str:
    total_sec = max(0, ms // 1000)
    minutes = total_sec // 60
    seconds = total_sec % 60
    return f"{minutes}:{seconds:02d}"

def format_remaining_ms(elapsed_ms: int, duration_ms: int) -> str:
    if duration_ms <= 0:
        return "-0:00"
    remaining_ms = max(0, duration_ms - elapsed_ms)
    total_sec = remaining_ms // 1000
    minutes = total_sec // 60
    seconds = total_sec % 60
    return f"-{minutes}:{seconds:02d}"

def calculate_progress(position_ms: int, duration_ms: int) -> float:
    if duration_ms <= 0:
        return 0.0
    return min(100.0, max(0.0, (position_ms / float(duration_ms)) * 100.0))

class TestMediaPayload(unittest.TestCase):
    def test_format_time(self):
        self.assertEqual(format_time_ms(0), "0:00")
        self.assertEqual(format_time_ms(45000), "0:45")
        self.assertEqual(format_time_ms(65000), "1:05")
        self.assertEqual(format_time_ms(235000), "3:55")
        self.assertEqual(format_time_ms(3600000), "60:00")

    def test_format_remaining_time(self):
        # 45s into a 180s track -> -2:15 remaining
        self.assertEqual(format_remaining_ms(45000, 180000), "-2:15")
        # 180s into 180s track -> -0:00
        self.assertEqual(format_remaining_ms(180000, 180000), "-0:00")
        # 0 duration -> -0:00
        self.assertEqual(format_remaining_ms(500, 0), "-0:00")

    def test_calculate_progress_zero_duration(self):
        # Progress must not raise ZeroDivisionError when duration is 0
        p = calculate_progress(500, 0)
        self.assertEqual(p, 0.0)

    def test_calculate_progress_normal(self):
        p = calculate_progress(30000, 60000)
        self.assertEqual(p, 50.0)

    def test_calculate_progress_clamped(self):
        # Position exceeds duration or negative
        p_over = calculate_progress(70000, 60000)
        self.assertEqual(p_over, 100.0)
        p_under = calculate_progress(-100, 60000)
        self.assertEqual(p_under, 0.0)

    def test_base64_thumbnail_validation(self):
        # Valid PNG byte sequence
        dummy_png_bytes = b"\x89PNG\r\n\x1a\n\x00\x00\x00\rIHDR"
        encoded = base64.b64encode(dummy_png_bytes).decode("ascii")
        data_uri = f"data:image/png;base64,{encoded}"

        self.assertTrue(data_uri.startswith("data:image/"))
        decoded = base64.b64decode(data_uri.split(",")[1])
        self.assertEqual(decoded[:4], b"\x89PNG")

if __name__ == "__main__":
    unittest.main()
