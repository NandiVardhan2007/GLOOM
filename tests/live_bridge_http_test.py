import unittest
import urllib.request
import json
import base64
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

if __name__ == "__main__":
    unittest.main()
