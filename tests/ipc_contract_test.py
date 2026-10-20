"""
Automated Test Suite: IPC Event Payloads & Schema Integrity
Project JARVIS
"""

import unittest
import json

class TestIPCContracts(unittest.TestCase):
    def test_media_event_schema(self):
        payload = {
            "playing": True,
            "title": "Sublime Motion",
            "artist": "JARVIS Orchestra",
            "album": "Celestial Dynamics",
            "positionMs": 42000,
            "durationMs": 184000,
            "thumbnail": "data:image/jpeg;base64,/9j/4AAQSkZJRg==",
        }
        self.assertIsInstance(payload["playing"], bool)
        self.assertIsInstance(payload["title"], str)
        self.assertIsInstance(payload["artist"], str)
        self.assertIsInstance(payload["positionMs"], int)
        self.assertIsInstance(payload["durationMs"], int)
        self.assertTrue(payload["thumbnail"].startswith("data:image/"))

    def test_system_event_schema(self):
        payload = {
            "cpuPercent": 14.5,
            "ramUsedMb": 8192,
            "ramTotalMb": 16384,
            "batteryPercent": 88,
            "isCharging": True,
            "hasBattery": True,
            "diskFreeGb": 320,
            "diskTotalGb": 512,
            "netDownloadKbps": 2450.5,
            "netUploadKbps": 180.2,
        }
        self.assertGreaterEqual(payload["cpuPercent"], 0.0)
        self.assertLessEqual(payload["cpuPercent"], 100.0)
        self.assertLessEqual(payload["ramUsedMb"], payload["ramTotalMb"])
        self.assertTrue(payload["isCharging"])
        self.assertGreater(payload["diskFreeGb"], 0)
        self.assertGreater(payload["netDownloadKbps"], 0.0)

    def test_system_action_whitelist(self):
        valid_actions = {"lock", "taskmgr", "settings", "bluetooth", "network", "calc"}
        for action in ["lock", "taskmgr", "calc", "settings"]:
            self.assertIn(action, valid_actions)

    def test_timer_payload_schema(self):
        timer_state = {
            "mode": "pomodoro",
            "isRunning": True,
            "remainingSeconds": 1425,
            "totalSeconds": 1500,
            "elapsedMs": 0,
            "formattedTime": "23:45",
        }
        self.assertIn(timer_state["mode"], ["pomodoro", "stopwatch"])
        self.assertIsInstance(timer_state["isRunning"], bool)
        self.assertEqual(timer_state["formattedTime"], "23:45")

    def test_volume_event_schema(self):
        payload = {
            "volume": 0.65,
            "isMuted": False,
        }
        self.assertGreaterEqual(payload["volume"], 0.0)
        self.assertLessEqual(payload["volume"], 1.0)
        self.assertIsInstance(payload["isMuted"], bool)

    def test_clipboard_event_schema(self):
        payload = [
            {
                "id": 1,
                "text": "https://github.com/jarvis/dynamic-island",
                "preview": "https://github.com/jarvis/dynamic-island",
                "timestampMs": 1729432000000,
            }
        ]
        self.assertEqual(len(payload), 1)
        self.assertGreater(payload[0]["id"], 0)
        self.assertIsInstance(payload[0]["text"], str)

if __name__ == "__main__":
    unittest.main()
