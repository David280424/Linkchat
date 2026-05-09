package com.example.textmemail.models

import com.google.firebase.Timestamp
import java.util.Date

data class Contact(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val isOnline: Boolean = false,
    val lastSeen: Long = 0L,
    val role: String = "user",
    val fcmToken: String? = null
)

data class Message(
    val id: String = "",
    val senderId: String = "",
    val receiverId: String = "",
    val text: String = "",
    val timestamp: Any? = null,
    val isRead: Boolean = false,
    val mediaUrl: String? = null,
    val mediaType: String? = null
) {
    fun getTimestampDate(): Date {
        return when (timestamp) {
            is Timestamp -> timestamp.toDate()
            is Long -> Date(timestamp)
            else -> Date(0)
        }
    }
}
