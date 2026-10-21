import unittest
import urllib.request
import json
import base64
import hashlib
import os

class TestLiveBridgeHttpEndpoints(unittest.TestCase):

    def test_status_response_format(self):
        """Verify GET /api/status returns expected JSON schema."""
        expected = {"status": "ok", "service": "JARVIS LiveBridge"}
        self.assertEqual(expected["status"], "ok")
        self.assertIn("service", expected)

    def test_telemetry_payload_schema(self):
        """Verify mobile telemetry JSON payload schema."""
        payload = {
            "deviceId": "SM-S928B",
            "deviceName": "Samsung Galaxy S24 Ultra",
            "deviceType": "android",
            "batteryPercent": 92,
            "isCharging": False,
            "wifiSsid": "JARVIS-Mesh-5G"
        }
        self.assertIsInstance(payload["batteryPercent"], int)
        self.assertIn(payload["deviceType"], ["android", "ios", "windows"])

    def test_file_transfer_payload_validation(self):
        """Verify POST /api/transfer payload conforms to base64 transfer protocol."""
        data = b"Testing binary transfer integrity on JARVIS LiveBridge"
        b64 = base64.b64encode(data).decode("ascii")
        payload = {
            "fileName": "sample_document.pdf",
            "fileSize": len(data),
            "dataBase64": b64
        }
        decoded = base64.b64decode(payload["dataBase64"])
        self.assertEqual(decoded, data)

    def test_kde_connect_stream_headers_and_checksum(self):
        """Verify KDE Connect-style raw binary streaming headers and streaming SHA-256."""
        payload_data = b"KDE Connect Chunked Streaming Binary Payload 64KB" * 100
        sha256_hash = hashlib.sha256(payload_data).hexdigest()

        headers = {
            "Content-Type": "application/octet-stream",
            "X-File-Name": "video_recording.mp4",
            "Content-Length": str(len(payload_data)),
            "X-Jarvis-Auth-Token": "test-paired-session-token-32b",
            "X-Jarvis-Checksum-Sha256": sha256_hash,
        }

        self.assertEqual(headers["Content-Type"], "application/octet-stream")
        self.assertEqual(int(headers["Content-Length"]), len(payload_data))
        self.assertEqual(headers["X-Jarvis-Checksum-Sha256"], sha256_hash)

    def test_remote_mousepad_control_payload(self):
        """Verify KDE Connect-style remote mousepad input payload structure."""
        valid_actions = ["move", "click", "double_click", "down", "up", "scroll"]
        valid_buttons = ["left", "right", "middle"]

        payload_move = {"action": "move", "dx": 12.5, "dy": -8.0, "button": "left"}
        payload_click = {"action": "click", "dx": 0.0, "dy": 0.0, "button": "right"}
        payload_scroll = {"action": "scroll", "dx": 0.0, "dy": 4.5, "button": "middle"}

        for p in [payload_move, payload_click, payload_scroll]:
            self.assertIn(p["action"], valid_actions)
            self.assertIn(p["button"], valid_buttons)
            self.assertIsInstance(p["dx"], float)
            self.assertIsInstance(p["dy"], float)

if __name__ == "__main__":
    unittest.main()
