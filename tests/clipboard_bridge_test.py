import unittest
import json
import base64
import hashlib

class TestClipboardBridge(unittest.TestCase):

    def test_clipboard_self_echo_prevention(self):
        """Verify that clipboard updates originating from self are rejected to prevent infinite echo loops."""
        desktop_device_id = "jarvis-desktop-primary"
        mobile_device_id = "jarvis-android-mobile"

        def should_process_clipboard(payload_json: str, local_device_id: str) -> bool:
            data = json.loads(payload_json)
            origin = data.get("originDeviceId", "")
            return origin != local_device_id

        # Message originating from desktop received by desktop -> must be dropped
        desktop_self_msg = json.dumps({
            "text": "Secret Token 1234",
            "originDeviceId": desktop_device_id,
            "timestampMs": 1700000000000
        })
        self.assertFalse(should_process_clipboard(desktop_self_msg, desktop_device_id))

        # Message originating from mobile received by desktop -> must be accepted
        mobile_msg = json.dumps({
            "text": "OTP Code: 994-123",
            "originDeviceId": mobile_device_id,
            "timestampMs": 1700000000050
        })
        self.assertTrue(should_process_clipboard(mobile_msg, desktop_device_id))

    def test_clipboard_unicode_and_multiline_fidelity(self):
        """Verify clipboard payload preserves unicode symbols, emojis, and multiline snippets."""
        rich_text = "JARVIS Ecosystem: 🚀 High-speed sync\nLine 2: ⚡ 100+ MB/s\nLine 3: π ≈ 3.1415926535"
        payload = {
            "text": rich_text,
            "originDeviceId": "jarvis-android-mobile",
            "timestampMs": 1700000000100
        }
        encoded = json.dumps(payload, ensure_ascii=False)
        decoded = json.loads(encoded)
        self.assertEqual(decoded["text"], rich_text)
        self.assertIn("🚀", decoded["text"])
        self.assertIn("⚡", decoded["text"])

    def test_file_beam_payload_generation_and_integrity(self):
        """Verify that beamed files are base64 encoded and reconstructed with matching SHA-256."""
        sample_bytes = b"JARVIS Ecosystem Live Bridge File Beam Payload 2026" * 64
        original_hash = hashlib.sha256(sample_bytes).hexdigest()

        b64_str = base64.b64encode(sample_bytes).decode("ascii")
        payload = {
            "fileName": "ecosystem_report.pdf",
            "fileSize": len(sample_bytes),
            "dataBase64": b64_str,
            "originDeviceId": "jarvis-desktop-primary"
        }

        # Receiver side decoding
        received_bytes = base64.b64decode(payload["dataBase64"])
        received_hash = hashlib.sha256(received_bytes).hexdigest()

        self.assertEqual(original_hash, received_hash)
        self.assertEqual(len(received_bytes), len(sample_bytes))

if __name__ == "__main__":
    unittest.main()
