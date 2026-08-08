# Mesh Talk: Testing & Quality Assurance Strategy

Testing a decentralized P2P network requires a multi-layered approach to ensure reliability across hardware variations and high-density environments.

---

## 1. Automated Testing Suite

### A. Unit Tests (Logic & Protocol)
Located in `app/src/test/java/`. These tests focus on core engine stability without requiring hardware.
- **Protocol Serialization**: Verifying that Protobuf `MeshPacket` types correctly wrap and unwrap `Handshake`, `Message`, and `SyncUpdate`.
- **Stealth ID Math**: Ensuring that two devices generate the same Stealth ID for the same public key within the same 15-minute time window.
- **Bloom Filter Performance**: Benchmarking false-positive rates with various message counts (50, 100, 500).

### B. Simulation Tests (Gossip & Suppression)
We use mock `MeshProtocol` instances to simulate high-density scenarios.
- **Suppression Logic**: Simulating 10 concurrent nodes hearing the same message to verify that only the expected number of nodes initiate a re-broadcast.

---

## 2. Integration & P2P Hardware Testing

P2P features are verified using a minimum of **three physical devices**:
1.  **Direct Chat**: Device A sends to B (1-hop).
2.  **Carrier Test**: Device A sends to C, with B acting as a `CARRYING` node (2-hops).
3.  **Cross-Version Test**: Ensuring v1.2.0 (Protobuf) rejects or correctly handles legacy connection attempts (if applicable).

### Testing Matrix
| Feature | Pass Criteria |
| :--- | :--- |
| **Foreground Service** | Mesh remains active 1 hour after app is swiped away. |
| **Movement Sensing** | Scan interval increases from 15s to 2m after 1 minute of non-movement. |
| **Auto-Read** | Opening a chat sends a `READ` SyncUpdate back to the sender. |
| **QR Setup** | Scanning a QR from Gallery correctly extracts ID and Name. |

---

## 3. Performance Benchmarking

- **Search Latency**: Measure time to find a specific string in a database of 10,000 mock messages (Target: <10ms using FTS5).
- **Battery Drain**: Monitor mA draw via Android Studio Profiler during "Continuous Search" vs. "Movement Sensing" idle.
- **Memory Pressure**: Ensure `ConcurrentHashMap` for reassembly doesn't exceed 20MB under heavy traffic.

---

## 4. Manual QA Checklist (Release Prep)

- [ ] Permissions granted on first launch (BT, Location, Camera, Notification).
- [ ] Profile name can be edited and saved.
- [ ] Public Shout can be toggled OFF and messages stop appearing.
- [ ] Blue Tick appears correctly after QR verification.
- [ ] Images are sent, received, and viewable in full screen.
