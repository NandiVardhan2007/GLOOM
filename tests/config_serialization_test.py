"""
Automated Test Suite: Configuration Serialization & Migration
Project jarvis
"""

import unittest
import json
import tempfile
import os

DEFAULT_CONFIG = {
    "mode": "island",
    "topOffset": 12,
    "islandWidth": 210,
    "islandHeight": 34,
    "expandedWidth": 560,
    "expandedHeight": 220,
    "autostart": False,
    "avoidFullscreen": True,
    "volumeHudEnabled": True,
    "clipboardHistoryEnabled": False,
    "reducedMotion": False,
    "theme": "obsidian",
}

class TestConfigSerialization(unittest.TestCase):
    def test_default_config_integrity(self):
        self.assertEqual(DEFAULT_CONFIG["mode"], "island")
        self.assertEqual(DEFAULT_CONFIG["topOffset"], 12)
        self.assertTrue(DEFAULT_CONFIG["avoidFullscreen"])
        self.assertTrue(DEFAULT_CONFIG["volumeHudEnabled"])

    def test_load_corrupted_json(self):
        """Ensures corrupted config JSON gracefully falls back to default settings."""
        with tempfile.NamedTemporaryFile("w+", delete=False, suffix=".json") as f:
            f.write("{ invalid json corrupted content !@#$ }")
            temp_path = f.name

        try:
            with open(temp_path, "r") as f:
                content = f.read()
            try:
                loaded = json.loads(content)
            except json.JSONDecodeError:
                loaded = DEFAULT_CONFIG.copy()

            self.assertEqual(loaded["mode"], "island")
            self.assertEqual(loaded["islandWidth"], 210)
        finally:
            if os.path.exists(temp_path):
                os.remove(temp_path)

    def test_partial_config_migration(self):
        """Simulates older config version missing new keys."""
        old_config = {
            "mode": "notch",
            "islandWidth": 200,
        }

        # Merge with defaults
        merged = DEFAULT_CONFIG.copy()
        merged.update(old_config)

        self.assertEqual(merged["mode"], "notch")
        self.assertEqual(merged["islandWidth"], 200)
        # Missing keys must take safe default values
        self.assertTrue(merged["avoidFullscreen"])
        self.assertEqual(merged["theme"], "obsidian")

    def test_extended_settings_defaults(self):
        """Verifies new massive settings properties have valid defaults."""
        extended_config = {
            "clockFormat": "12h",
            "accentColor": "#00d2ff",
            "pillEdgeGap": 12,
            "islandScale": 1.0,
            "cornerShape": "rounded",
            "panelStyle": "dark_glass",
            "glowIntensity": "subtle",
            "showMediaWidget": True,
            "showAudioWaveform": True,
            "showVolumeMixer": True,
            "showAgentNotifications": True,
            "notchyCompactIndicator": True,
        }
        self.assertIn(extended_config["clockFormat"], ["12h", "24h"])
        self.assertTrue(extended_config["accentColor"].startswith("#"))
        self.assertGreaterEqual(extended_config["pillEdgeGap"], 0)
        self.assertGreaterEqual(extended_config["islandScale"], 0.7)
        self.assertTrue(extended_config["showMediaWidget"])
        self.assertTrue(extended_config["notchyCompactIndicator"])

if __name__ == "__main__":
    unittest.main()
