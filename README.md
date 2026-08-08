# Mesh Talk

**Mesh Talk** is a decentralized, high-performance peer-to-peer messaging application for Android that works entirely over **Bluetooth Low Energy (BLE)**. Designed for scale and efficiency, it allows communication without internet, cellular networks, or central servers.

---

## 🚀 Key Features

-   **Zero Infrastructure**: Chat directly device-to-device using BLE.
-   **Scale-Ready Architecture**: Optimized to handle high-density mesh environments with 1B+ user potential.
-   **Secure by Design**:
    *   **End-to-End Encryption**: Messages encrypted using Google Tink (ECIES).
    *   **Cryptographic Identity**: Peers identified by Ed25519 public keys.
-   **Intelligent Networking**:
    *   **Binary Protocol (Protobuf)**: Uses Google Protocol Buffers for 50% smaller network packets compared to JSON.
    *   **Adaptive Scanning**: Uses the device's **accelerometer** to search for peers more frequently when moving and conserve battery when stationary.
    *   **Bloom Filters**: Uses 512-bit Bloom Filters for ultra-efficient message synchronization.
-   **Advanced UI & Performance**:
    *   **Active Status Indicator**: Real-time visual feedback (green dot) when a peer is actively connected in the mesh.
    *   **FTS5 Search**: Instantaneous search across millions of messages using SQLite Full-Text Search.
    *   **Adaptive UI**: Modern Material 3 interface that scales from phones to foldables and tablets.

---

## 🛠️ Tech Stack & Optimizations

Mesh Talk implements industrial-grade optimizations for decentralized communication:

| Feature | Implementation | Benefit |
| :--- | :--- | :--- |
| **Serialization** | **Protobuf (Lite)** | Halves payload size; reduces BLE fragmentation. |
| **Syncing** | **Bloom Filters** | Sync missing messages with 90% less data exchange. |
| **Battery** | **Movement Sensing** | Reduces scanning duty cycle by 8x when phone is on a desk. |
| **Persistence** | **Room + FTS5** | Sub-millisecond search; handles massive local message history. |
| **Media** | **Filesystem Storage** | Avatars stored as local files to prevent DB bloat and UI lag. |
| **Concurrency** | **Kotlin Coroutines/Flow** | Fully reactive, non-blocking BLE stack. |

---

## 📥 Installation Guide

### Prerequisites
- **Android Device**: Running Android 12 (API 31) or higher.
- **Hardware**: Bluetooth Low Energy (BLE) support.
- **Permissions**: Bluetooth (Scan, Advertise, Connect), Location (required for BLE), and Nearby Devices.

### Build from Source
1.  **Clone the repository**:
    ```bash
    git clone https://github.com/inzamulhoque/MeshTalk.git
    ```
2.  **Open in Android Studio**:
    *   Open Android Studio (Ladybug or newer).
3.  **Protobuf Generation**:
    *   The project uses the `com.google.protobuf` plugin. Run a Gradle sync to automatically generate the Java/Kotlin sources from `.proto` files.
4.  **Run**:
    *   Connect two devices and click `Run 'app'`.

---

## 📖 User Guide & Settings

### Adaptive Scanning
In **Settings**, toggle **Adaptive Scanning**. This uses your phone's sensors to detect movement. If the phone is stationary, the mesh search slows down to save battery. Once you pick up the phone, it immediately resumes high-frequency scanning.

### Message Pruning
Manage your local storage under **Data Management**:
- **Prune Others' Messages**: Auto-delete received messages older than X days (Default: 30).
- **Prune My Messages**: Optionally auto-delete your own sent history after X months.

### Active Indicators
In the peer list, a **green dot** on an avatar signifies a live GATT connection. You can exchange messages with these peers with zero latency.

---

## 🏗️ Architecture Detail

### BLE Protocol Layer
Messages are fragmented into chunks fitting the MTU (typically ~180-500 bytes). Each chunk is prefixed with a 4-byte header (`0xCC`, `msgId`, `chunkIndex`, `totalChunks`) for reliable reassembly.

### Identity Handshake
When two devices meet, they perform a secure handshake exchanging:
1.  Public Identity Key (Ed25519)
2.  Ephemeral Session Key (Tink/ECIES)
3.  **Bloom Filter** of local message inventory.

---

## 🗺️ Roadmap
- [x] Protobuf binary protocol migration.
- [x] Adaptive sensor-based mesh networking.
- [x] Bloom Filter synchronization.
- [x] FTS5 Message Search.
- [ ] **Multi-hop Routing (Gossip v2)**: Non-flooding probabilistic forwarding.
- [ ] **Stealth Addressing**: Rotating BLE IDs to prevent physical tracking.
- [ ] **Offline Maps Integration**: Share location markers over the mesh.

---

## 📜 License

Distributed under the MIT License. See `LICENSE` for more information.

---

## 📧 Contact

**Inzamul Hoque** - [inzamol@gmail.com](mailto:inzamol@gmail.com)  
Project Link: [https://github.com/inzamol/MeshTalk](https://github.com/inzamol/MeshTalk)
