# Mesh Talk: Security & Threat Model

This document outlines the security architecture of Mesh Talk, the potential threats it faces, and the cryptographic mitigations implemented to protect user privacy and data integrity in a decentralized environment.

---

## 1. Cryptographic Foundations

Mesh Talk utilizes the **Google Tink** library for industrial-grade, multi-language compatible cryptography.

| Component | Primitive | Algorithm | Purpose |
| :--- | :--- | :--- | :--- |
| **Identity** | Digital Signature | **Ed25519** | Long-term peer identification and message origin proof. |
| **Encryption** | Hybrid Encryption | **ECIES (P-256 HKDF-HMAC-SHA256)** | Protecting message content from intermediate mesh nodes. |
| **Session Security** | Authenticated Encryption | **AES128-GCM** | Symmetric encryption for message payloads with integrity checks. |
| **Local Storage** | Keystore-backed AES | **AES256-GCM** | Protecting local database and settings on the device. |

---

## 2. Threat Analysis & Mitigations

### A. Network Eavesdropping (Packet Sniffing)
*   **Threat**: An attacker with a BLE sniffer captures mesh packets to read user messages.
*   **Mitigation**: **End-to-End Encryption (E2EE)**. Messages are encrypted using the receiver's public key before entering the mesh. Intermediate carrier nodes only see encrypted Protobuf blobs.

### B. Physical Tracking (Stalking)
*   **Threat**: Malicious actors log specific BLE identifiers at different locations to track a user's physical movement.
*   **Mitigation**: **Rotating Stealth IDs**. The BLE advertising identifier changes every 15 minutes. It is derived from a one-way SHA-256 hash of the public key and a time-window salt, making physical tracking mathematically infeasible for non-verified observers.

### C. Man-in-the-Middle (MitM)
*   **Threat**: An attacker intercepts a handshake and provides a fake public key to intercept future messages.
*   **Mitigation**: **Out-of-Band Verification (QR Code)**. Users are encouraged to verify identities via physical QR code scanning. This "pins" the peer's public key, ensuring that any future key changes are flagged as high-risk.

### D. Message Replay Attacks
*   **Threat**: An attacker captures an encrypted message and broadcasts it multiple times to harass the user or trigger duplicate processing.
*   **Mitigation**: **Timestamp & UUID Validation**. Every message contains a unique UUID and a nanosecond-precision timestamp. The app rejects any message with a UUID already present in its FTS5 index.

### E. Sybil Attacks (Mesh Flooding)
*   **Threat**: An attacker creates thousands of virtual nodes to overwhelm the mesh with garbage data.
*   **Mitigation**: 
    1. **Proof-of-Work (PoW)**: Every message requires a computational nonce. This makes mass-spamming expensive for attackers while remaining cheap for legitimate users.
    2. **Sliding Window Rate Limiting**: The mesh protocol drops packets from peers exceeding 30 msgs/min (Standard) or 150 msgs/min (Verified).
    3. **Gossip v2 Suppression**: Prevents redundant re-broadcasts in high-density areas.

---

## 3. Data Privacy (Local)

- **Android Keystore**: Private keys are generated and stored inside the hardware-backed Android Keystore (StrongBox where available). They never leave the device and cannot be extracted even with root access.
- **Auto-Pruning**: To minimize the "forensic footprint," users can configure aggressive message pruning, ensuring that old conversations are physically deleted from the SQLite database.

---

## 4. Security Recommendations for Users

1.  **Verify Contacts**: Only trust users with a Blue Tick (Verified via QR).
2.  **Enable Movement Sensing**: Keeps the mesh stealthy by reducing radio activity when stationary.
3.  **Use Screen Lock**: Protects the local database from physical access.
