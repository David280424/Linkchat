package com.example.textmemail.models

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
    val timestamp: Long = 0L,
    val isRead: Boolean = false,
    val mediaUrl: String? = null,
    val mediaType: String? = null // "image" o "audio"
)
