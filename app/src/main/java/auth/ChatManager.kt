package auth

import android.net.Uri
import com.example.textmemail.models.Contact
import com.example.textmemail.models.Message
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage

object ChatManager {
    private val db by lazy { FirebaseFirestore.getInstance() }
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val storage by lazy { FirebaseStorage.getInstance() }

    private fun chatIdWith(otherUid: String): String {
        val me = auth.currentUser?.uid ?: ""
        return if (me <= otherUid) "${me}_${otherUid}" else "${otherUid}_${me}"
    }

    /** Envío de texto con metadatos para la lista de chats y participantes */
    fun sendMessage(receiverUid: String, text: String, onResult: (Boolean, String?) -> Unit = { _, _ -> }) {
        val meUid = auth.currentUser?.uid ?: return
        val chatId = chatIdWith(receiverUid)
        val timestamp = System.currentTimeMillis()
        
        val data = mapOf(
            "senderId" to meUid,
            "receiverId" to receiverUid,
            "text" to text,
            "timestamp" to timestamp,
            "isRead" to false
        )
        
        db.collection("chats").document(chatId).collection("messages").add(data)
        
        // Actualizar el documento del chat para la lista principal ordenado por tiempo
        val chatUpdate = mapOf(
            "lastMessage" to text,
            "lastTimestamp" to timestamp,
            "lastSenderId" to meUid,
            "participants" to listOf(meUid, receiverUid)
        )
        db.collection("chats").document(chatId).set(chatUpdate, SetOptions.merge())
            .addOnSuccessListener { onResult(true, null) }
            .addOnFailureListener { onResult(false, it.localizedMessage) }
    }

    /** Envío de media con metadatos para la lista de chats */
    fun sendMediaMessage(
        receiverUid: String,
        uri: Uri,
        mediaType: String, // "image" or "audio"
        onResult: (ok: Boolean, error: String?) -> Unit
    ) {
        val meUid = auth.currentUser?.uid ?: return
        val chatId = chatIdWith(receiverUid)
        val timestamp = System.currentTimeMillis()
        val extension = if (mediaType == "image") "jpg" else "aac"
        val ref = storage.reference.child("chat_media/$chatId/$timestamp.$extension")

        ref.putFile(uri).addOnSuccessListener {
            ref.downloadUrl.addOnSuccessListener { downloadUrl ->
                val text = if(mediaType == "image") "📷 Foto" else "🎤 Audio"
                val data = mapOf(
                    "senderId" to meUid,
                    "receiverId" to receiverUid,
                    "text" to text,
                    "timestamp" to timestamp,
                    "isRead" to false,
                    "mediaUrl" to downloadUrl.toString(),
                    "mediaType" to mediaType
                )
                db.collection("chats").document(chatId).collection("messages").add(data)
                
                val chatUpdate = mapOf(
                    "lastMessage" to text,
                    "lastTimestamp" to timestamp,
                    "lastSenderId" to meUid,
                    "participants" to listOf(meUid, receiverUid)
                )
                db.collection("chats").document(chatId).set(chatUpdate, SetOptions.merge())
                onResult(true, null)
            }
        }.addOnFailureListener { onResult(false, it.localizedMessage) }
    }

    fun listenForMessages(otherUid: String, onMessages: (List<Message>) -> Unit): ListenerRegistration {
        val chatId = chatIdWith(otherUid)
        val meUid = auth.currentUser?.uid ?: ""
        
        return db.collection("chats").document(chatId).collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, _ ->
                val list = snapshot?.documents?.mapNotNull { it.toObject(Message::class.java)?.copy(id = it.id) } ?: emptyList()
                onMessages(list)
                
                // Marcar como leídos cuando se reciben los mensajes (si soy el receptor)
                snapshot?.documents?.forEach { doc ->
                    if (doc.getString("receiverId") == meUid && doc.getBoolean("isRead") == false) {
                        doc.reference.update("isRead", true)
                    }
                }
            }
    }

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

    fun deleteMessage(otherUid: String, messageId: String, onResult: (Boolean, String?) -> Unit = { _, _ -> }) {
        val chatId = chatIdWith(otherUid)
        db.collection("chats").document(chatId).collection("messages").document(messageId).delete()
            .addOnSuccessListener { onResult(true, null) }
            .addOnFailureListener { onResult(false, it.localizedMessage) }
    }

    fun normalizePhone(phone: String): String {
        val clean = phone.replace(Regex("[^0-9+]"), "")
        return if (clean.length == 10 && !clean.startsWith("+")) "+52$clean" else clean
    }
}
