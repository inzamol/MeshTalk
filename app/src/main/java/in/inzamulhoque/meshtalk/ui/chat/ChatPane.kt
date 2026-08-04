package `in`.inzamulhoque.meshtalk.ui.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import `in`.inzamulhoque.meshtalk.data.local.entity.Message
import `in`.inzamulhoque.meshtalk.data.local.entity.MessageStatus
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatPane(
    viewModel: ChatViewModel,
    myId: String,
    onBack: () -> Unit,
    isTwoPane: Boolean,
    modifier: Modifier = Modifier
) {
    val peer by viewModel.peer.collectAsState()
    val messages by viewModel.messages.collectAsState()
    var text by remember { mutableStateOf("") }
    var showMenu by remember { mutableStateOf(false) }
    var messageToDelete by remember { mutableStateOf<Message?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize().imePadding(),
        topBar = {
            TopAppBar(
                title = { Text(peer?.displayName ?: "Chat") },
                navigationIcon = {
                    if (!isTwoPane) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "Menu")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Delete Chat") },
                            onClick = {
                                viewModel.deleteChat()
                                showMenu = false
                                if (!isTwoPane) onBack()
                            },
                            leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) }
                        )
                    }
                }
            )
        },
        bottomBar = {
            BottomAppBar(
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                TextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Type a message...") },
                    shape = RoundedCornerShape(24.dp),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = {
                    if (text.isNotBlank()) {
                        viewModel.sendMessage(text)
                        text = ""
                    }
                }) {
                    Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Send")
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            reverseLayout = false,
            contentPadding = PaddingValues(8.dp)
        ) {
            items(messages, key = { it.uuid }) { message ->
                MessageBubble(
                    message = message,
                    isMine = message.senderId == myId,
                    onLongClick = { messageToDelete = message },
                    onRetryClick = { viewModel.retryMessage(message) }
                )
            }
        }
    }

    if (messageToDelete != null) {
        AlertDialog(
            onDismissRequest = { messageToDelete = null },
            title = { Text("Delete Message") },
            text = { Text("Are you sure you want to delete this message?") },
            confirmButton = {
                TextButton(onClick = {
                    messageToDelete?.let { viewModel.deleteMessage(it) }
                    messageToDelete = null
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { messageToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: Message, 
    isMine: Boolean, 
    onLongClick: () -> Unit,
    onRetryClick: () -> Unit
) {
    val alignment = if (isMine) Alignment.CenterEnd else Alignment.CenterStart
    val containerColor = if (isMine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
    val contentColor = if (isMine) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
    
    val shape = if (isMine) {
        RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)
    } else {
        RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp)
    }

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = alignment) {
        Surface(
            color = containerColor,
            contentColor = contentColor,
            shape = shape,
            modifier = Modifier
                .padding(vertical = 4.dp, horizontal = 8.dp)
                .combinedClickable(
                    onClick = {
                        if (message.status == MessageStatus.FAILED) {
                            onRetryClick()
                        }
                    },
                    onLongClick = onLongClick
                )
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (message.status == MessageStatus.FAILED) {
                        Text(
                            "FAILED ",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Text(
                        text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp)),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.alpha(0.7f)
                    )
                    if (isMine) {
                        Spacer(modifier = Modifier.width(4.dp))
                        MessageStatusIcon(status = message.status)
                    }
                }
            }
        }
    }
}

@Composable
fun MessageStatusIcon(status: MessageStatus) {
    when (status) {
        MessageStatus.PENDING -> Icon(
            Icons.Rounded.Schedule, 
            contentDescription = "Pending",
            modifier = Modifier.size(12.dp),
            tint = MaterialTheme.colorScheme.outline
        )
        MessageStatus.SENT -> Icon(
            Icons.Rounded.Done, 
            contentDescription = "Sent",
            modifier = Modifier.size(12.dp),
            tint = MaterialTheme.colorScheme.outline
        )
        MessageStatus.DELIVERED -> Icon(
            Icons.Rounded.DoneAll, 
            contentDescription = "Delivered",
            modifier = Modifier.size(12.dp),
            tint = MaterialTheme.colorScheme.outline
        )
        MessageStatus.READ -> Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.DoneAll,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = Color(0xFF00BFFF)
            )
            Icon(
                Icons.Rounded.Done,
                contentDescription = "Read",
                modifier = Modifier
                    .size(12.dp)
                    .offset(x = (-8).dp),
                tint = Color(0xFF00BFFF)
            )
        }
        MessageStatus.FAILED -> Icon(
            Icons.Rounded.Error, 
            contentDescription = "Failed",
            modifier = Modifier.size(12.dp),
            tint = MaterialTheme.colorScheme.error
        )
        MessageStatus.CARRYING -> Icon(
            Icons.Rounded.Sync, 
            contentDescription = "Carrying",
            modifier = Modifier.size(12.dp),
            tint = MaterialTheme.colorScheme.secondary
        )
    }
}
