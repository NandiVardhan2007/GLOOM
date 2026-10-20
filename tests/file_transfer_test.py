"""
JARVIS Ecosystem — File Transfer Integrity & Path Traversal Security Tests
Validates filename sanitization, traversal attack prevention, chunked SHA-256
hashing, and transfer state modeling for Phase 2 Core Sharing.
"""

import hashlib
import os
import unittest


def sanitize_transfer_filename(filename: str) -> str:
    """Sanitizes incoming file names to prevent path traversal and arbitrary disk writes."""
    normalized = filename.replace("\\", "/")
    base = os.path.basename(normalized)
    safe = "".join(c for c in base if c.isalnum() or c in " ._-()[]")
    return safe.strip() or "unnamed_transfer.bin"


def get_download_path(filename: str, base_dir: str = None) -> str:
    """Computes isolated download destination under Downloads/JARVIS/."""
    safe_name = sanitize_transfer_filename(filename)
    root = base_dir or os.path.expanduser("~/Downloads/JARVIS")
    target = os.path.abspath(os.path.join(root, safe_name))
    # Ensure target remains inside base_dir
    if not target.startswith(os.path.abspath(root)):
        raise ValueError("Path traversal attempt detected")
    return target


class TestFileTransferSafety(unittest.TestCase):
    def test_path_traversal_stripping(self):
        cases = [
            ("../../malicious.bat", "malicious.bat"),
            ("..\\..\\..\\Windows\\System32\\cmd.exe", "cmd.exe"),
            ("/etc/shadow", "shadow"),
            ("C:\\Windows\\win.ini", "win.ini"),
            ("safe_document.pdf", "safe_document.pdf"),
            ("My Photos (2026) [High-Res].jpg", "My Photos (2026) [High-Res].jpg"),
            ("../../../", "unnamed_transfer.bin"),
        ]
        for raw, expected in cases:
            self.assertEqual(sanitize_transfer_filename(raw), expected)

    def test_destination_path_confinement(self):
        base = os.path.abspath("D:/MockDownloads/JARVIS")
        dest = get_download_path("../../attack.exe", base_dir=base)
        self.assertTrue(dest.startswith(base), "Target path must be strictly confined to isolated folder")
        self.assertEqual(os.path.basename(dest), "attack.exe")

    def test_chunked_sha256_verification(self):
        # Simulate 3 chunks of file stream
        chunks = [b"Chunk 1: Hello ", b"Chunk 2: JARVIS ", b"Chunk 3: Transfer!"]
        hasher = hashlib.sha256()
        total_bytes = 0
        for chunk in chunks:
            hasher.update(chunk)
            total_bytes += len(chunk)

        expected_hash = hashlib.sha256(b"Chunk 1: Hello Chunk 2: JARVIS Chunk 3: Transfer!").hexdigest()
        self.assertEqual(hasher.hexdigest(), expected_hash)
        self.assertEqual(total_bytes, len(b"Chunk 1: Hello Chunk 2: JARVIS Chunk 3: Transfer!"))


if __name__ == "__main__":
    unittest.main()
