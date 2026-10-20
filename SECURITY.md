# JARVIS Ecosystem — Security & Trust Model

**Document Version:** 1.0.0  
**Last Updated:** 2026-10-20  
**Status:** Mandatory Engineering Standard  

---

## 1. Core Principles

JARVIS operates as a privileged local assistant across the user's primary computing devices (Windows and Android). Security is a core product requirement, not an afterthought.

1. **Zero Trust Local Network:** Never trust a device simply because it appears on the local Wi-Fi network. All communication requires authenticated pairing.
2. **Credential Inviolability:** Never capture, log, store, extract, or transmit lock patterns, PINs, passwords, or biometric data. Never attempt to bypass the Android or Windows lock screens.
3. **Least Privilege & Scoped Capabilities:** Features operate under minimum necessary OS permissions. Features can be enabled or revoked individually.
4. **Human in the Loop for Consequential Actions:** High-impact or irreversible actions strictly require user confirmation before execution.
5. **Prompt Injection Defense:** Content ingested from websites, messages, emails, notifications, and clipboard must be treated as untrusted data, never as system instructions.

---

## 2. Threat Model & Mitigations

| Threat | Impact | Mitigation Strategy |
|---|---|---|
| **Rogue LAN Device Spoofing** | Unauthorized access to clipboard or files. | Cryptographic key exchange during initial user-confirmed pairing; TLS encrypted channel; message origin verification. |
| **Clipboard Sync Loop** | Network flood & infinite echo. | Every clipboard event carries a unique `originDeviceId` and timestamp; incoming clipboard events with matching local device ID are dropped. |
| **Prompt Injection via Messages/Web** | Agent tricked into executing unauthorized tools. | Strict separation between untrusted user input data and system prompt; typed tool schemas with allowlisted parameter ranges; human confirmation gate. |
| **Lock Screen Bypass Attempt** | Security boundary breach. | Agent detects lock state and immediately halts automation, requesting normal manual user unlock. |
| **Malicious File Ingestion** | Remote code execution or disk overwrite. | Incoming file transfers require user accept prompt; files are written strictly to isolated `Downloads/JARVIS/` directory; path traversal attacks (`../`) are blocked. |
| **Runaway Automation** | Unintended mouse/keyboard actions. | Global emergency stop hotkey (`Ctrl + Alt + Esc`); visible pulsating automation banner; hard execution timeout per step (30s). |

---

## 3. Human Approval Policy (Consequential Actions)

The agent is strictly prohibited from autonomously executing the following actions without explicit, interactive human confirmation:

- **Sending Messages / Emails:** Must present recipient, subject, and exact body preview to user before dispatching.
- **Placing Phone Calls:** Must display phone number and contact name for user approval.
- **Financial Transactions:** Cannot submit payments, authorize purchases, or checkout carts.
- **Data Deletion:** Cannot delete or overwrite files, emails, or contacts without explicit per-item confirmation.
- **System & Security Changes:** Cannot modify firewall rules, install system certificates, alter passwords, or elevate privileges.

---

## 4. Audit Logging & Privacy Hygiene

- **Sensitive Field Redaction:** Passwords, tokens, credit card numbers, and detected OTPs are redacted from persistent debug logs (`[REDACTED_SECRET]`).
- **Local Storage:** Configuration and pairing keys are stored in the platform's secure credential store (Windows Credential Manager / Android KeyStore).
- **Zero Telemetry Leaks:** Telemetry data (CPU, RAM, battery) stays strictly local to the peer-to-peer connection and is never uploaded to remote servers.
