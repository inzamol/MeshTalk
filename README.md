# Mesh Talk v1.3.0

**Mesh Talk** is a decentralized, high-performance peer-to-peer messaging application for Android that works entirely over **Bluetooth Low Energy (BLE)**. Engineered for extreme scale and privacy, it enables reliable communication in high-density environments without internet, cellular networks, or central servers.

---

## Key Features

-   **Zero Infrastructure**: Chat directly device-to-device using BLE.
-   **Production-Ready Persistence**: Runs as a **Foreground Service** with a persistent notification, ensuring the mesh stays active 24/7.
-   **Robust Background Work**: Offloads heavy tasks (ID rotation, DB pruning) to **WorkManager**, ensuring they run only when the device is charging and idle.
-   **Scalable Mesh Architecture**: Optimized to handle high-density environments (stadiums, protests) with **billions of users potential**.
-   **Decentralized Anti-Spam**:
    *   **Proof-of-Work (PoW)**: Every message requires a SHA-256 computational proof to prevent mass-spam botnets.
    *   **Rate Limiting**: Sliding-window throttling (15-30 msgs/min) with increased throughput for **Verified Peers**.
-   **Privacy & Anti-Tracking**:
    *   **Rotating Stealth IDs**: Advertising identifiers rotate every 15 minutes to prevent physical tracking by BLE sniffers.
    *   **neverForLocation**: Optimized for Android 12+ to allow mesh functionality without requiring GPS/Location services to be active.
    *   **End-to-End Encryption**: All personal messages are encrypted using industrial-grade **Google Tink (ECIES)**.
-   **Intelligent Networking**:
    *   **Hardware Filtering**: BLE scans are filtered at the chip level, waking the CPU only for Mesh Talk traffic.
    *   **Gossip v2 (Density Control)**: Counter-based suppression prevents "broadcast storms" in crowded areas.
    *   **Adaptive Scanning**: Uses the **Significant Motion Sensor** to throttle mesh activity when stationary, preserving battery life.
-   **Modern Communication UI**:
    *   **Live Status**: Real-time **Online** (Green dot) and **Last Seen** indicators in chat headers.
    *   **Polished Chat**: WhatsApp/Telegram style bubble alignment with **Triple Blue Ticks** for READ status.
    *   **Rich QR Discovery**: Scan to instantly verify and start a chat.
    *   **Public Shout**: Dedicated broadcast channel with global toggle.

---

## Tech Stack & Optimizations

Mesh Talk implements industrial-grade optimizations for decentralized communication:

| Feature | Implementation | Benefit |
| :--- | :--- | :--- |
| **Persistence** | **Foreground Service** | Keeps the mesh node alive in the background indefinitely. |
| **Maintenance** | **WorkManager** | Ensures DB pruning and ID rotation run reliably without draining battery. |
| **Efficiency** | **BroadcastReceivers** | Event-driven status monitoring consumes zero CPU when idle. |
| **Congestion** | **Gossip v2 + Throttling** | Prevents radio frequency collapse and deters mesh spam. |
| **Privacy** | **neverForLocation Flag** | Decouples mesh discovery from GPS; works with Location OFF. |
| **Battery** | **Significant Motion** | Main CPU sleeps until the phone is physically moved. |
| **Hardware** | **On-Chip BLE Filter** | Filters mesh packets at the radio layer; saves >40% power. |
| **Media** | **Filesystem Storage** | Avatars stored as files to ensure butter-smooth UI scrolling. |

---

## Installation Guide

### Prerequisites
- **Android Device**: Running Android 12 (API 31) or higher.
- **Hardware**: BLE support and a Camera.
- **Permissions**: 
    - **Bluetooth**: Required for Mesh (Scan, Advertise, Connect).
    - **Location**: Optional (Not required for mesh functionality on Android 12+).
    - **Camera**: Required for Peer Verification via QR.
    - **Notifications**: Required for the Mesh Service and Message alerts.

### Build from Source
1.  **Clone the repository**:
    ```bash
    git clone https://github.com/inzamulhoque/MeshTalk.git
    ```
2.  **Open in Android Studio** (Ladybug or newer).
3.  **Sync & Build**: Run Gradle sync to generate Protobuf sources and indices.
4.  **Signing Configuration**: To build a signed release APK, create a `signing.properties` file in the project root (this file is ignored by Git).
    
    **Example `signing.properties`:**
    ```properties
    STORE_FILE=release.jks
    STORE_PASSWORD=your_keystore_password
    KEY_ALIAS=your_key_alias
    KEY_PASSWORD=your_key_password
    ```
5.  **Run**: Connect devices and click `Run 'app'`.

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
