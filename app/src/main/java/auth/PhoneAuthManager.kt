package auth

import android.app.Activity
import com.google.firebase.FirebaseException
import com.google.firebase.auth.*
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.util.concurrent.TimeUnit

class PhoneAuthManager(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private var verificationId: String? = null

    /** Envía enlace de recuperación al correo */
    fun sendPasswordReset(email: String, done: (Boolean, String) -> Unit) {
        if (email.isBlank()) {
            done(false, "Introduce un correo válido")
            return
        }
        auth.sendPasswordResetEmail(email)
            .addOnSuccessListener { done(true, "Enlace de recuperación enviado") }
            .addOnFailureListener { done(false, "Error: ${it.localizedMessage}") }
    }

    /** Acceso rápido Admin */
    fun quickAdminLogin(done: (ok: Boolean, message: String) -> Unit) {
        auth.signInWithEmailAndPassword("admin@textmemail.com", "admin123456")
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    if (user != null) {
                        db.collection("users").document(user.uid).set(
                            mapOf("role" to "admin", "isOnline" to true, "updatedAt" to FieldValue.serverTimestamp()), 
                            SetOptions.merge()
                        )
                        done(true, "Acceso Admin Correcto")
                    }
                } else done(false, "Error: ${task.exception?.localizedMessage}")
            }
    }

    fun setUserOnlineStatus(isOnline: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).update("isOnline", isOnline, "lastSeen", FieldValue.serverTimestamp())
    }

    fun sendVerificationCode(activity: Activity, phoneNumber: String, onCodeSent: (Boolean, String?) -> Unit) {
        val finalPhone = if (phoneNumber.startsWith("+")) phoneNumber else if (phoneNumber.length == 10) "+52$phoneNumber" else phoneNumber
        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(finalPhone)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(credential: PhoneAuthCredential) {}
                override fun onVerificationFailed(e: FirebaseException) { onCodeSent(false, e.localizedMessage) }
                override fun onCodeSent(id: String, token: PhoneAuthProvider.ForceResendingToken) {
                    verificationId = id
                    onCodeSent(true, null)
                }
            }).build()
        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    /** Lógica de verificación y registro vinculando Email/Pass */
    fun verifyCode(
        code: String, 
        name: String, 
        email: String, 
        pass: String, 
        language: String, 
        isRegister: Boolean,
        done: (ok: Boolean, message: String) -> Unit
    ) {
        val id = verificationId ?: return done(false, "Error de sesión (verificationId nulo)")
        val credential = PhoneAuthProvider.getCredential(id, code)
        
        auth.signInWithCredential(credential).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val user = auth.currentUser ?: return@addOnCompleteListener
                
                if (isRegister && email.isNotBlank() && pass.isNotBlank()) {
                    val emailCred = EmailAuthProvider.getCredential(email, pass)
                    user.linkWithCredential(emailCred).addOnCompleteListener { 
                        updateProfile(user, name, email, language, done)
                    }
                } else {
                    updateProfile(user, name, email, language, done)
                }
            } else {
                done(false, "Código incorrecto")
            }
        }
    }

    private fun updateProfile(user: FirebaseUser, name: String, email: String, language: String, done: (Boolean, String) -> Unit) {
        db.collection("users").document(user.uid).get().addOnSuccessListener { snap ->
            val doc = mutableMapOf<String, Any>(
                "phone" to (user.phoneNumber ?: ""),
                "isOnline" to true,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!snap.exists()) {
                doc["name"] = name
                doc["email"] = email
                doc["language"] = language
                doc["role"] = if (user.phoneNumber == "+528114805140") "admin" else "user"
                doc["createdAt"] = FieldValue.serverTimestamp()
            } else {
                if (name.isNotBlank()) doc["name"] = name
                if (email.isNotBlank()) doc["email"] = email
            }
            db.collection("users").document(user.uid).set(doc, SetOptions.merge())
                .addOnSuccessListener { done(true, "Acceso correcto") }
                .addOnFailureListener { done(false, it.localizedMessage ?: "Error al guardar perfil") }
        }
    }

    fun signOut() { setUserOnlineStatus(false); auth.signOut() }
    
    fun getCurrentUserRole(done: (ok: Boolean, role: String?, message: String) -> Unit) {
        val uid = auth.currentUser?.uid ?: return done(false, null, "")
        db.collection("users").document(uid).get().addOnSuccessListener { done(true, it.getString("role"), "") }
    }

    fun isCurrentUserAdmin(): Boolean {
        val u = auth.currentUser ?: return false
        return u.email == "admin@textmemail.com" || u.phoneNumber == "+528114805140"
    }

    fun updateLanguage(lang: String, done: (Boolean, String) -> Unit) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).update("language", lang).addOnSuccessListener { done(true, "") }
    }

    fun deleteUserFromFirestore(uid: String, done: (Boolean, String) -> Unit) {
        db.collection("users").document(uid).delete().addOnSuccessListener { done(true, "") }
    }
}
