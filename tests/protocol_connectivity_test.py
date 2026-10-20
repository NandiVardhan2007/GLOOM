"""
JARVIS Ecosystem — Protocol Connectivity & Deduplication Tests
Validates the versioned JARVIS Connect protocol schema, loop prevention,
heartbeat lifecycle, and device presence handling.
"""

import time
import unittest


class MockProtocolEngine:
    def __init__(self, local_device_id: str, local_device_name: str):
        self.local_device_id = local_device_id
        self.local_device_name = local_device_name
        self.seen_ids = set()
        self.id_order = []
        self.max_history = 1000
        self.paired_device_id = None
        self.paired_device_name = None
        self.last_heartbeat_ms = int(time.time() * 1000)
        self.state = "discovering"

    def is_duplicate_or_self(self, message_id: str, origin_id: str) -> bool:
        # Drop echo messages from self
        if origin_id == self.local_device_id:
            return True
        if message_id in self.seen_ids:
            return True

        self.seen_ids.add(message_id)
        self.id_order.append(message_id)
        if len(self.id_order) > self.max_history:
            oldest = self.id_order.pop(0)
            self.seen_ids.remove(oldest)
        return False

    def check_version(self, version: str) -> bool:
        return version.startswith("1.")

    def update_presence(self, device_id: str, device_name: str, timestamp_ms: int = None):
        self.state = "connected"
        self.paired_device_id = device_id
        self.paired_device_name = device_name
        self.last_heartbeat_ms = timestamp_ms or int(time.time() * 1000)

    def get_status(self, current_time_ms: int = None):
        now = current_time_ms or int(time.time() * 1000)
        if self.state == "connected" and (now - self.last_heartbeat_ms > 15000):
            return "reconnecting"
        return self.state


class TestProtocolConnectivity(unittest.TestCase):
    def setUp(self):
        self.desktop = MockProtocolEngine("jarvis-desktop-primary", "JARVIS Desktop")
        self.mobile = MockProtocolEngine("jarvis-mobile-galaxy-s24", "Galaxy S24 Ultra")

    def test_protocol_versioning(self):
        self.assertTrue(self.desktop.check_version("1.0.0"))
        self.assertTrue(self.desktop.check_version("1.2.5"))
        self.assertFalse(self.desktop.check_version("2.0.0"))
        self.assertFalse(self.desktop.check_version("0.9.0"))

    def test_loop_prevention_self_messages(self):
        # When desktop broadcasts a clipboard event, it should drop incoming packets with its own origin
        is_dup = self.desktop.is_duplicate_or_self("msg-1001", "jarvis-desktop-primary")
        self.assertTrue(is_dup, "Must drop messages originating from local device ID")

    def test_deduplication_peer_messages(self):
        # First receipt of message from mobile
        first = self.desktop.is_duplicate_or_self("msg-2001", "jarvis-mobile-galaxy-s24")
        self.assertFalse(first, "First message from peer must not be marked duplicate")

        # Second receipt of same message ID (e.g. UDP retransmit or network duplicate)
        second = self.desktop.is_duplicate_or_self("msg-2001", "jarvis-mobile-galaxy-s24")
        self.assertTrue(second, "Repeated message ID must be dropped as duplicate")

    def test_presence_heartbeat_and_reconnection(self):
        self.assertEqual(self.desktop.get_status(), "discovering")

        now = int(time.time() * 1000)
        self.desktop.update_presence("jarvis-mobile-galaxy-s24", "Galaxy S24 Ultra", timestamp_ms=now)
        self.assertEqual(self.desktop.get_status(now), "connected")

        # After 5 seconds of no packets -> still connected
        self.assertEqual(self.desktop.get_status(now + 5000), "connected")

        # After 16 seconds of network silence -> transitions to reconnecting
        self.assertEqual(self.desktop.get_status(now + 16000), "reconnecting")

        # Upon receiving fresh presence packet -> resumes connected
        self.desktop.update_presence("jarvis-mobile-galaxy-s24", "Galaxy S24 Ultra", timestamp_ms=now + 16050)
        self.assertEqual(self.desktop.get_status(now + 16100), "connected")


if __name__ == "__main__":
    unittest.main()
