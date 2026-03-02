// app/src/main/java/com/example/textmemail/models.kt
package com.example.textmemail.models

data class Contact(
    val uid: String = "",
    val name: String = "",
    val email: String = ""
)

/**
 * Modelo que mapea 1:1 con los documentos en /chats/{chatId}/messages/{messageId}
 */
data class Message(
    val id: String = "",          // doc.id (lo rellenamos al leer)
    val senderId: String = "",    // uid del remitente
    val receiverId: String = "",  // uid del destinatario
    val text: String = "",        // contenido
    val timestamp: Long = 0L      // System.currentTimeMillis()
)