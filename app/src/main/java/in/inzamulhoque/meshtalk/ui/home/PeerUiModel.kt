package `in`.inzamulhoque.meshtalk.ui.home

import `in`.inzamulhoque.meshtalk.data.local.entity.Peer

data class PeerUiModel(
    val peer: Peer,
    val unreadCount: Int = 0,
    val lastMessage: String? = null,
    val lastMessageTime: Long = 0
)
