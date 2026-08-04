# Mesh Talk

**Mesh Talk** is a decentralized, peer-to-peer messaging application for Android that works entirely over **Bluetooth Low Energy (BLE)**. It allows users to communicate without the internet, cellular networks, or any central server, making it ideal for off-grid communication, crowded events, or privacy-conscious users.

## 🚀 Key Features

-   **Zero Infrastructure**: Chat directly device-to-device using BLE.
-   **Adaptive UI**: Modern Material 3 interface that adapts to phones, tablets, and foldables.
-   **Secure by Design**:
    *   **End-to-End Encryption**: Messages are encrypted using Google Tink (ECIES).
    *   **Cryptographic Identity**: Peers are identified by Ed25519 public keys.
-   **Reliable Delivery**:
    *   **Manual Fragmentation**: Large messages are split and reassembled to bypass Bluetooth MTU limits.
    *   **Proactive Sync**: Immediate connection attempt when entering a chat.
-   **Privacy**: No phone numbers or emails required. Use your device's Bluetooth name or a custom alias.

---

## 🛠️ Prerequisites

-   **Android Device**: Running Android 12 (API 31) or higher.
-   **Hardware**: Bluetooth Low Energy (BLE) support.
-   **Permissions**: The app requires:
    *   Bluetooth (Scan, Advertise, Connect)
    *   Location (Required by Android for BLE scanning)
    *   Nearby Devices

---

## 📥 Installation Guide

### From Source
1.  **Clone the repository**:
    ```bash
    git clone https://github.com/yourusername/MeshTalk.git
    ```
2.  **Open in Android Studio**:
    *   Open Android Studio (Ladybug or newer recommended).
    *   Select `File > Open` and navigate to the cloned folder.
3.  **Sync Gradle**:
    *   Wait for the project to sync. Ensure you have the **Kotlin 2.1.0+** and **Compose 1.7+** compilers.
4.  **Run the App**:
    *   Connect at least two Android devices.
    *   Click `Run 'app'` and select your devices.

---

## 📖 User Guide

### 1. Initial Setup
*   Upon first launch, grant the requested Bluetooth and Location permissions.
*   Ensure **Bluetooth** and **GPS/Location** are toggled **ON** in your system settings.

### 2. Discovering Peers
*   Stay on the main screen ("Mesh Talk" list).
*   Peers running the app nearby will automatically appear in your list with their device names (e.g., "Galaxy S24").

### 3. Starting a Chat
*   Tap on a peer's name to enter their chat room.
*   The app will proactively establish a secure connection and sync any pending messages.

### 4. Sending Messages
*   Type your message and hit the Send icon.
*   If the peer is nearby, the message will sync immediately.
*   If the peer is away, the message is stored locally and will sync the next time you are in range.

---

## 🏗️ Architecture

Mesh Talk uses modern Android development practices:
-   **Jetpack Compose**: For the entire UI layer.
-   **Material 3 Adaptive**: For multi-pane layouts.
-   **Navigation 3**: For state-driven navigation.
-   **Room**: For local persistence of messages and peer identities.
-   **Google Tink**: For industrial-grade cryptographic operations.
-   **Coroutines & Flow**: For reactive, non-blocking BLE communication.

---

## 🛠️ Troubleshooting

-   **Peer Not Showing Up**: 
    *   Ensure **Location (GPS)** is turned ON. Android requires this for BLE scanning.
    *   Check if the other device is also on the main screen of the app.
-   **"Decryption Failed" Toast**:
    *   This happens if a peer's identity has changed. Go to system settings and **Clear App Data** on both devices to reset the secure handshake.
-   **"Failed to Send Chunk"**:
    *   Bluetooth interference may be high. Try moving the devices closer or toggling Bluetooth OFF and ON.

---

## 🗺️ Roadmap
- [ ] Multi-hop mesh routing (forwarding messages through intermediate peers).
- [ ] Image and file sharing support.
- [ ] Group chat capabilities.
- [ ] Custom user profiles and avatars.

---

## 🤝 Contribution Guide

We welcome contributions! To contribute:

1.  **Fork** the project.
2.  **Create a Feature Branch**: `git checkout -b feature/AmazingFeature`.
3.  **Commit your changes**: `git commit -m 'Add some AmazingFeature'`.
4.  **Push to the Branch**: `git push origin feature/AmazingFeature`.
5.  **Open a Pull Request**.

### Coding Standards
*   Follow [Kotlin style guides](https://kotlinlang.org/docs/coding-conventions.html).
*   Ensure all new BLE protocol changes are documented in the Handshake/Message DTOs.
*   Verify UI changes on both phone and tablet layouts.

---

## 📜 License

Distributed under the MIT License. See `LICENSE` for more information.

---

## 📧 Contact

**Inzamul Hoque** - [inzamol@gmail.com](mailto:inzamol@gmail.com)  
Project Link: [https://github.com/inzamol/MeshTalk](https://github.com/inzamol/MeshTalk)
