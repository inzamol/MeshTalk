package `in`.inzamulhoque.meshtalk.data.local

import androidx.room.TypeConverter
import `in`.inzamulhoque.meshtalk.data.local.entity.MessageStatus

class Converters {
    @TypeConverter
    fun fromMessageStatus(status: MessageStatus): String {
        return status.name
    }

    @TypeConverter
    fun toMessageStatus(status: String): MessageStatus {
        return MessageStatus.valueOf(status)
    }
}
