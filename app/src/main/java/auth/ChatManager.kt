// app/src/main/java/auth/ChatManager.kt
package auth

import com.example.textmemail.models.Contact
import com.example.textmemail.models.Message
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query


object ChatManager {
    private val db by lazy { FirebaseFirestore.getInstance() }
    private val auth by lazy { FirebaseAuth.getInstance() }

    private fun chatIdWith(otherUid: String): String {
        val me = auth.currentUser?.uid ?: ""
        return if (me <= otherUid) "${me}_${otherUid}" else "${otherUid}_${me}"
    }

    fun sendMessage(
        receiverUid: String,
        text: String,
        onResult: (ok: Boolean, error: String?) -> Unit
    ) {
        val me = auth.currentUser?.uid
        if (me.isNullOrBlank()) {
            onResult(false, "No hay usuario autenticado")
            return
        }
        val chatId = chatIdWith(receiverUid)
        val data = mapOf(
            "senderId" to me,
            "receiverId" to receiverUid,
            "text" to text,
            "timestamp" to System.currentTimeMillis()
        )
        db.collection("chats").document(chatId).collection("messages")
            .add(data)
            .addOnSuccessListener { onResult(true, null) }
            .addOnFailureListener { e -> onResult(false, e.localizedMessage) }
    }

    fun listenForMessages(
        otherUid: String,
        onMessages: (List<Message>) -> Unit
    ): ListenerRegistration {
        val chatId = chatIdWith(otherUid)
        return db.collection("chats").document(chatId).collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null) return@addSnapshotListener
                val list = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(Message::class.java)?.copy(id = doc.id)
                }
                onMessages(list)
            }
    }
}