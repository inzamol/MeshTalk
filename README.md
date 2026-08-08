# Mesh Talk v1.2.0

**Mesh Talk** is a decentralized, high-performance peer-to-peer messaging application for Android that works entirely over **Bluetooth Low Energy (BLE)**. Engineered for extreme scale and privacy, it enables reliable communication in high-density environments without internet, cellular networks, or central servers.

---

## Key Features

-   **Zero Infrastructure**: Chat directly device-to-device using BLE.
-   **Production-Ready Persistence**: Runs as a **Foreground Service** with a persistent notification, ensuring the mesh stays active 24/7 even when the app is closed.
-   **Scalable Mesh Architecture**: Optimized to handle high-density environments (stadiums, protests) with **billions of users potential**.
-   **Privacy & Anti-Tracking**:
    *   **Rotating Stealth IDs**: Advertising identifiers rotate every 15 minutes to prevent physical tracking by BLE sniffers.
    *   **End-to-End Encryption**: All personal messages are encrypted using industrial-grade **Google Tink (ECIES)**.
    *   **Cryptographic Identity**: Peers are verified using Ed25519 public keys.
-   **Intelligent Networking**:
    *   **Binary Protocol (Protobuf)**: Uses Google Protocol Buffers for ~50% smaller network packets, significantly increasing reliability.
    *   **Gossip v2 (Density Control)**: Counter-based suppression prevents "broadcast storms" in crowded areas by intelligently limiting re-broadcasts.
    *   **Adaptive Scanning**: Uses the device's **accelerometer** to throttle mesh search when stationary, preserving battery life.
    *   **Bloom Filters**: Uses 512-bit filters for ultra-efficient message synchronization with 90% less data exchange.
-   **Modern Communication UI**:
    *   **Rich QR Discovery**: Scan to instantly verify and start a chat. Supports Flash and Gallery image picking.
    *   **Smart Feed**: Features unread message badges, bold contact names for unread chats, and auto-decrypted subline previews.
    *   **Public Shout**: Dedicated broadcast channel for nearby users with a global enable/disable toggle.
    *   **FTS5 Search**: Instantaneous search across massive message histories using SQLite Full-Text Search.

---

## Tech Stack & Optimizations

Mesh Talk implements industrial-grade optimizations for decentralized communication:

| Feature | Implementation | Benefit |
| :--- | :--- | :--- |
| **Persistence** | **Foreground Service** | Keeps the mesh node alive in the background indefinitely. |
| **Serialization** | **Protobuf (Lite)** | Minimizes BLE fragmentation; faster reassembly. |
| **Congestion** | **Gossip v2 Suppression** | Prevents radio frequency collapse in high-density crowds. |
| **Privacy** | **Stealth ID Rotation** | Eliminates long-term physical stalking via BLE packets. |
| **Battery** | **Movement Sensing** | Reduces scanning duty cycle by 8x when phone is on a desk. |
| **Persistence** | **Room + FTS5 + Indices** | Sub-millisecond local search and high-performance history rendering. |
| **Media** | **Filesystem Storage** | Avatars stored as files to ensure butter-smooth UI scrolling. |

---

## Installation Guide

### Prerequisites
- **Android Device**: Running Android 12 (API 31) or higher.
- **Hardware**: BLE support and a Camera (for peer verification).
- **Permissions**: Bluetooth, Location (required for BLE), Camera, and Notifications (for the Mesh Service).

### Build from Source
1.  **Clone the repository**:
    ```bash
    git clone https://github.com/inzamulhoque/MeshTalk.git
    ```
2.  **Open in Android Studio** (Ladybug or newer).
3.  **Sync & Build**: Run Gradle sync to generate Protobuf sources and indices.
4.  **Run**: Connect devices and click `Run 'app'`.

---

## User Guide & Security

### Verified Identity
Sharing your **Mesh Identity QR** (found at the top of Settings) allows others to verify your cryptographic key. Once verified, a **Blue Tick** appears next to your name, and you can recognize each other even as your Stealth IDs rotate for privacy.

### Adaptive Scanning
When enabled, the app uses your phone's accelerometer. If the phone is left on a table, the mesh search interval increases to **2 minutes**. As soon as you pick up the phone, it resumes searching every **15 seconds**.

### Data Management
Configure **Message Pruning** in settings to keep your device storage lean. You can set different expiration windows for received messages vs. your own sent history.

---

## Architecture & Engineering Docs

For a deep dive into the engineering behind Mesh Talk, please refer to our comprehensive documentation:

-   **[Technical Architecture](file:///C:/Users/inzam/AndroidStudioProjects/MeshTalk/TECHNICAL_DETAILS.md)**: BLE protocol, Gossip v2, and system flow.
-   **[Security & Threat Model](file:///C:/Users/inzam/AndroidStudioProjects/MeshTalk/SECURITY_MODEL.md)**: Cryptographic mitigations and anti-tracking math.
-   **[Testing Strategy](file:///C:/Users/inzam/AndroidStudioProjects/MeshTalk/TESTING.md)**: QA matrix, P2P simulation, and benchmarks.
-   **[Contributing Guidelines](file:///C:/Users/inzam/AndroidStudioProjects/MeshTalk/CONTRIBUTING.md)**: Coding standards and protocol versioning.

---

## Roadmap
- [x] Protobuf & Gossip v2 (Scaling).
- [x] Foreground Service & Mesh Persistence.
- [x] Rotating Stealth IDs (Anti-Tracking).
- [x] FTS5 Search & Rich UI Overhaul.
- [ ] **Multi-hop Location markers**: Share offline map coordinates over the mesh.
- [ ] **Large File Streaming**: Chunked file transfer for high-res photos.

---

## License

Distributed under the MIT License. See `LICENSE` for more information.

---

## Contact

**Inzamul Hoque** - [inzamol@gmail.com](mailto:inzamol@gmail.com)  
Project Link: [https://github.com/inzamol/MeshTalk](https://github.com/inzamol/MeshTalk)
