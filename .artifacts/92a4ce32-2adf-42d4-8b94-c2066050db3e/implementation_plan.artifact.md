# Implementation Plan - UI with Jetpack Compose and Navigation 3

This plan outlines the steps to build the Mesh Talk user interface using Jetpack Compose and Navigation 3, integrating E2EE and the mesh protocol.

## User Review Required

> [!IMPORTANT]
> The app will use `androidx.navigation3` for navigation and `androidx.compose.material3.adaptive` for multi-pane layouts.
> Messages sent from the UI will be inserted into the database and automatically synchronized via the mesh protocol.

## Proposed Changes

### Navigation & Routes

#### [NEW] [NavRoutes.kt](file:///C:/Users/inzam/AndroidStudioProjects/MeshTalk/app/src/main/java/in/inzamulhoque/meshtalk/ui/navigation/NavRoutes.kt)
Define `@Serializable` routes for `Home` (Peer List) and `Chat` (Message History).

### ViewModels

#### [NEW] [HomeViewModel.kt](file:///C:/Users/inzam/AndroidStudioProjects/MeshTalk/app/src/main/java/in/inzamulhoque/meshtalk/ui/home/HomeViewModel.kt)
Handle the list of discovered peers from the database.

#### [NEW] [ChatViewModel.kt](file:///C:/Users/inzam/AndroidStudioProjects/MeshTalk/app/src/main/java/in/inzamulhoque/meshtalk/ui/chat/ChatViewModel.kt)
Handle message history for a specific peer and provide a way to send messages.

### UI Components

#### [NEW] [MainScreen.kt](file:///C:/Users/inzam/AndroidStudioProjects/MeshTalk/app/src/main/java/in/inzamulhoque/meshtalk/ui/MainScreen.kt)
The root adaptive screen using `ListDetailPaneScaffold` and Navigation 3.

#### [NEW] [PeerListPane.kt](file:///C:/Users/inzam/AndroidStudioProjects/MeshTalk/app/src/main/java/in/inzamulhoque/meshtalk/ui/home/PeerListPane.kt)
A composable displaying the list of peers.

#### [NEW] [ChatPane.kt](file:///C:/Users/inzam/AndroidStudioProjects/MeshTalk/app/src/main/java/in/inzamulhoque/meshtalk/ui/chat/ChatPane.kt)
A composable displaying the chat history and input field.

### Activity Integration

#### [MODIFY] [MainActivity.kt](file:///C:/Users/inzam/AndroidStudioProjects/MeshTalk/app/src/main/java/in/inzamulhoque/meshtalk/MainActivity.kt)
Update to use `MainScreen` and set up the Navigation 3 backstack.

## Verification Plan

### Automated Tests
- Unit tests for `ChatViewModel` to verify message insertion and signing/encryption.

### Manual Verification
- Verify adaptive layout on phone (single pane) and tablet/desktop (dual pane).
- Verify navigation between peer list and chat.
- Verify message history updates when new messages arrive in the database.
