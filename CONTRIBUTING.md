# Contributing to Mesh Talk

We're excited you're interested in contributing to Mesh Talk! To maintain the quality and security of the decentralized mesh, we follow strict coding and architectural guidelines.

---

## 1. Development Workflow

- **Branching**: We use a modified Git Flow.
    - `main`: Stable production releases.
    - `develop`: Ongoing feature integration.
    - `feature/*`: Work-in-progress features (branch off `develop`).
- **Pull Requests**: All PRs must target the `develop` branch and require at least one successful build and manual verification on physical hardware.

---

## 2. Coding Standards

### A. Kotlin Styles
- Follow the official [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html).
- Use **functional primitives** where possible (`map`, `filter`, `flatMap`).
- Prefer `StateFlow` and `Flow` for reactive data streams.

### B. Architecture (MVVM + Adaptive)
- **ViewModel**: All business logic and P2P coordination should reside in ViewModels.
- **Compose**: UI must be fully declarative. Avoid using `android.view.View` directly unless wrapping for CameraX.
- **Adaptive UI**: Use Material 3 Adaptive components (ListDetailPaneScaffold) to ensure the app works on tablets and foldables.

---

## 3. Protocol Changes (IMPORTANT)

Since Mesh Talk is a distributed system, changes to the communication protocol are high-impact.
1.  **Protobuf**: Any new message fields must be added to `app/src/main/proto/mesh_protocol.proto`.
2.  **Versioning**: Maintain backward compatibility for at least one minor version to prevent network fragmentation.
3.  **Documentation**: Update `TECHNICAL_DETAILS.md` and `SECURITY_MODEL.md` if any cryptographic or routing logic changes.

---

## 4. Setting Up Your Environment

1.  **Hardware**: You MUST have at least two physical Android devices (API 31+) for testing. BLE behaves differently on emulators.
2.  **Linting**: Run `./gradlew lint` before submitting a PR.
3.  **Signing**: Do not commit your `signing.properties` or `.jks` files.

---

## 5. Community & Ethics

- **Privacy First**: Any feature that compromises user anonymity or enables tracking will be rejected.
- **Open Source**: Mesh Talk is MIT Licensed. Let's keep the mesh open and accessible to everyone.
