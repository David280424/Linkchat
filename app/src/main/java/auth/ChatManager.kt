// app/src/main/java/auth/ChatManager.kt
package auth

import com.example.textmemail.models.Contact
import com.example.textmemail.models.Message
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions


object ChatManager {
    private val db by lazy { FirebaseFirestore.getInstance() }
    private val auth by lazy { FirebaseAuth.getInstance() }

    /** Genera un ID de chat basado en números de teléfono para que sea universal */
    fun chatIdByPhone(phoneA: String, phoneB: String): String {
        val pA = normalizePhone(phoneA)
        val pB = normalizePhone(phoneB)
        return if (pA <= pB) "chat_${pA}_${pB}" else "chat_${pB}_${pA}"
    }

    /** Limpia el número de teléfono para que siempre tenga el mismo formato (+52...) */
    fun normalizePhone(phone: String): String {
        val clean = phone.replace(Regex("[^0-9+]"), "")
        return if (clean.length == 10 && !clean.startsWith("+")) "+52$clean" else clean
    }

    private fun chatIdWith(otherUid: String): String {
        val me = auth.currentUser?.uid ?: ""
        return if (me <= otherUid) "${me}_${otherUid}" else "${otherUid}_${me}"
    }

    fun sendMessage(
        receiverUid: String,
        text: String,
        receiverPhone: String? = null,
        onResult: (ok: Boolean, error: String?) -> Unit = { _, _ -> }
    ) {
        val meUid = auth.currentUser?.uid ?: return
        val myPhone = auth.currentUser?.phoneNumber ?: ""
        
        // Si tenemos teléfono, usamos la nueva lógica de "buzón universal"
        if (!receiverPhone.isNullOrBlank()) {
            val chatId = chatIdByPhone(myPhone, receiverPhone)
            val data = mapOf(
                "senderId" to meUid,
                "senderPhone" to myPhone,
                "receiverPhone" to normalizePhone(receiverPhone),
                "text" to text,
                "timestamp" to System.currentTimeMillis(),
                "isRead" to false
            )
            db.collection("chats_by_phone").document(chatId).collection("messages").add(data)
            db.collection("chats_by_phone").document(chatId).set(mapOf(
                "lastMessage" to text,
                "phones" to listOf(myPhone, normalizePhone(receiverPhone))
            ), SetOptions.merge())
        }

        // Mantenemos compatibilidad con la lógica de UID existente
        val chatIdUid = chatIdWith(receiverUid)
        val dataUid = mapOf(
            "senderId" to meUid,
            "receiverId" to receiverUid,
            "text" to text,
            "timestamp" to System.currentTimeMillis(),
            "isRead" to false
        )
        db.collection("chats").document(chatIdUid).collection("messages").add(dataUid)
        db.collection("chats").document(chatIdUid).set(mapOf("lastMessage" to text), SetOptions.merge())
    }

    fun listenForMessages(otherUid: String, otherPhone: String? = null, onMessages: (List<Message>) -> Unit): ListenerRegistration {
        val meUid = auth.currentUser?.uid ?: ""
        val myPhone = auth.currentUser?.phoneNumber ?: ""

        // Si hay teléfono, escuchamos el buzón universal
        if (!otherPhone.isNullOrBlank() && myPhone.isNotBlank()) {
            val chatId = chatIdByPhone(myPhone, otherPhone)
            return db.collection("chats_by_phone").document(chatId).collection("messages")
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .addSnapshotListener { snapshot, _ ->
                    val list = snapshot?.documents?.mapNotNull { it.toObject(Message::class.java)?.copy(id = it.id) } ?: emptyList()
                    onMessages(list)
                }
        }

        // Fallback a UID
        val chatIdUid = chatIdWith(otherUid)
        return db.collection("chats").document(chatIdUid).collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, _ ->
                val list = snapshot?.documents?.mapNotNull { it.toObject(Message::class.java)?.copy(id = it.id) } ?: emptyList()
                onMessages(list)
            }
    }

    // --- SEÑALIZACIÓN DE VIDEOLLAMADAS ---
    fun startCallSignal(otherUid: String, channelName: String) {
        val chatId = chatIdWith(otherUid)
        db.collection("chats").document(chatId).set(
            mapOf("activeCall" to mapOf(
                "callerId" to auth.currentUser?.uid,
                "channelName" to channelName,
                "status" to "ringing"
            )), SetOptions.merge()
        )
    }

    fun endCallSignal(otherUid: String) {
        val chatId = chatIdWith(otherUid)
        db.collection("chats").document(chatId).update("activeCall", null)
    }

    fun listenForChatInfo(otherUid: String, onUpdate: (Map<String, Any>?) -> Unit): ListenerRegistration {
        val chatId = chatIdWith(otherUid)
        return db.collection("chats").document(chatId).addSnapshotListener { doc, _ -> onUpdate(doc?.data) }
    }

    fun deleteMessage(otherUid: String, messageId: String, onResult: (ok: Boolean, error: String?) -> Unit) {
        val chatId = chatIdWith(otherUid)
        db.collection("chats").document(chatId).collection("messages").document(messageId).delete()
    }
}
