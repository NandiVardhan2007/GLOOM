"""
Automated Test Suite: Security, Hardened Bridge & Verified Desktop Updater
JARVIS Cross-Platform System
"""

import unittest
import hashlib
import time

class MockUpdaterSecurityValidator:
    ALLOWED_HOST_PREFIXES = (
        "https://github.com/NandiVardhan2007/JARVIS/releases/download/",
        "https://objects.githubusercontent.com/",
    )

    @classmethod
    def validate_url(cls, url: str) -> bool:
        return any(url.startswith(prefix) for prefix in cls.ALLOWED_HOST_PREFIXES)

    @staticmethod
    def parse_semver(v: str):
        cleaned = v.strip().lstrip("v").split("-")[0]
        parts = [int(p) for p in cleaned.split(".") if p.isdigit()]
        while len(parts) < 3:
            parts.append(0)
        return tuple(parts[:3])

    @classmethod
    def is_valid_upgrade(cls, current_version: str, target_version: str) -> bool:
        return cls.parse_semver(target_version) > cls.parse_semver(current_version)

    @staticmethod
    def verify_pe_header(binary_bytes: bytes) -> bool:
        # Must start with MZ (0x4D, 0x5A)
        return len(binary_bytes) >= 2 and binary_bytes[:2] == b"MZ"

    @staticmethod
    def verify_sha256(data: bytes, expected_hash_hex: str) -> bool:
        computed = hashlib.sha256(data).hexdigest()
        return computed.lower() == expected_hash_hex.strip().lower()


class MockLiveBridgeSecurityValidator:
    def __init__(self, expected_token: str, max_clock_skew_sec: int = 60):
        self.expected_token = expected_token
        self.max_clock_skew_sec = max_clock_skew_sec
        self.seen_nonces = set()

    def validate_request(self, auth_header: str, timestamp_sec: int, nonce: str) -> (bool, str):
        # 1. Token validation
        token = auth_header.replace("Bearer ", "").strip()
        if token != self.expected_token:
            return False, "Unauthorized token"

        # 2. Clock skew validation (replay defense)
        now_sec = int(time.time())
        skew = abs(now_sec - timestamp_sec)
        if skew > self.max_clock_skew_sec:
            return False, f"Clock skew too large: {skew}s > {self.max_clock_skew_sec}s"

        # 3. Nonce uniqueness validation
        if nonce in self.seen_nonces:
            return False, "Nonce replay detected"
        self.seen_nonces.add(nonce)

        return True, "OK"


class TestSecurityAndUpdater(unittest.TestCase):
    def test_updater_approved_hosts(self):
        valid_urls = [
            "https://github.com/NandiVardhan2007/JARVIS/releases/download/v1.0.5/jarvis.exe",
            "https://objects.githubusercontent.com/github-production-release-asset-2e65be/artifact.exe",
        ]
        for url in valid_urls:
            self.assertTrue(MockUpdaterSecurityValidator.validate_url(url), f"Should approve {url}")

    def test_updater_rejects_unapproved_hosts_and_cleartext(self):
        invalid_urls = [
            "http://github.com/NandiVardhan2007/JARVIS/releases/download/v1.0.5/jarvis.exe", # HTTP cleartext
            "https://evil-attacker.com/malicious_jarvis.exe",
            "https://github.com.attacker.com/release.exe",
            "file:///C:/Windows/System32/cmd.exe",
        ]
        for url in invalid_urls:
            self.assertFalse(MockUpdaterSecurityValidator.validate_url(url), f"Must reject {url}")

    def test_updater_downgrade_protection(self):
        # Upgrade: allowed
        self.assertTrue(MockUpdaterSecurityValidator.is_valid_upgrade("1.0.0", "1.0.1"))
        self.assertTrue(MockUpdaterSecurityValidator.is_valid_upgrade("v1.0.0", "v1.1.0"))
        self.assertTrue(MockUpdaterSecurityValidator.is_valid_upgrade("1.0.9", "2.0.0"))

        # Same version: rejected
        self.assertFalse(MockUpdaterSecurityValidator.is_valid_upgrade("1.0.0", "1.0.0"))

        # Downgrade: rejected
        self.assertFalse(MockUpdaterSecurityValidator.is_valid_upgrade("1.0.2", "1.0.1"))
        self.assertFalse(MockUpdaterSecurityValidator.is_valid_upgrade("2.0.0", "1.9.9"))

    def test_updater_pe_header_validation(self):
        valid_pe = b"MZ" + b"\x00" * 1024
        self.assertTrue(MockUpdaterSecurityValidator.verify_pe_header(valid_pe))

        invalid_pe = b"\x7fELF" + b"\x00" * 1024 # Linux binary
        self.assertFalse(MockUpdaterSecurityValidator.verify_pe_header(invalid_pe))

        script = b"powershell -Command calc.exe"
        self.assertFalse(MockUpdaterSecurityValidator.verify_pe_header(script))

    def test_updater_sha256_checksum_verification(self):
        payload = b"Sample verified binary payload for JARVIS update"
        correct_hash = hashlib.sha256(payload).hexdigest()

        self.assertTrue(MockUpdaterSecurityValidator.verify_sha256(payload, correct_hash))
        self.assertFalse(MockUpdaterSecurityValidator.verify_sha256(payload + b"tamper", correct_hash))

    def test_livebridge_auth_token_enforcement(self):
        validator = MockLiveBridgeSecurityValidator("secret_device_token_xyz")
        now = int(time.time())

        # Correct token
        ok, _ = validator.validate_request("Bearer secret_device_token_xyz", now, "nonce-1")
        self.assertTrue(ok)

        # Wrong token
        ok, err = validator.validate_request("Bearer invalid_token", now, "nonce-2")
        self.assertFalse(ok)
        self.assertIn("Unauthorized", err)

    def test_livebridge_replay_protection_timestamp_skew(self):
        validator = MockLiveBridgeSecurityValidator("token123", max_clock_skew_sec=60)
        now = int(time.time())

        # Acceptable skew (+15s)
        ok, _ = validator.validate_request("token123", now + 15, "nonce-a")
        self.assertTrue(ok)

        # Unacceptable skew (expired request 120s ago)
        ok, err = validator.validate_request("token123", now - 120, "nonce-b")
        self.assertFalse(ok)
        self.assertIn("Clock skew", err)

    def test_livebridge_replay_protection_duplicate_nonce(self):
        validator = MockLiveBridgeSecurityValidator("token123")
        now = int(time.time())

        # First request with nonce
        ok, _ = validator.validate_request("token123", now, "unique-nonce-12345")
        self.assertTrue(ok)

        # Second request replaying identical nonce
        ok, err = validator.validate_request("token123", now, "unique-nonce-12345")
        self.assertFalse(ok)
        self.assertIn("replay", err.lower())


if __name__ == "__main__":
    unittest.main()
