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

    /** Acceso rápido para el administrador usando Email/Password (Bypass de SMS) */
    fun quickAdminLogin(done: (ok: Boolean, message: String) -> Unit) {
        // IMPORTANTE: Esta cuenta debe existir en Firebase Auth (Email/Password)
        auth.signInWithEmailAndPassword("admin@textmemail.com", "admin123456")
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    if (user != null) {
                        val doc = mapOf(
                            "email" to "admin@textmemail.com",
                            "name" to "Administrador",
                            "role" to "admin",
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                        db.collection("users").document(user.uid)
                            .set(doc, SetOptions.merge())
                            .addOnSuccessListener { done(true, "Acceso Admin Correcto") }
                            .addOnFailureListener { e -> done(false, "Error al actualizar perfil admin: ${e.localizedMessage}") }
                    }
                } else {
                    done(false, "Error: Crea la cuenta admin@textmemail.com con pass admin123456 en Firebase Auth.")
                }
            }
    }

    fun sendVerificationCode(
        activity: Activity,
        phoneNumber: String,
        onCodeSent: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(credential: PhoneAuthCredential) {}

                override fun onVerificationFailed(e: FirebaseException) {
                    onError(e.localizedMessage ?: "Error al enviar código")
                }

                override fun onCodeSent(
                    verificationId: String,
                    token: PhoneAuthProvider.ForceResendingToken
                ) {
                    this@PhoneAuthManager.verificationId = verificationId
                    onCodeSent(verificationId)
                }
            })
            .build()
        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    fun verifyCode(
        code: String,
        name: String,
        language: String,
        done: (ok: Boolean, message: String) -> Unit
    ) {
        val id = verificationId ?: return done(false, "No se ha enviado el código.")
        val credential = PhoneAuthProvider.getCredential(id, code)
        signInWithPhoneAuthCredential(credential, name, language, done)
    }

    private fun signInWithPhoneAuthCredential(
        credential: PhoneAuthCredential,
        name: String,
        language: String,
        done: (ok: Boolean, message: String) -> Unit
    ) {
        auth.signInWithCredential(credential)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    if (user != null) {
                        val doc = mutableMapOf(
                            "phone" to (user.phoneNumber ?: ""),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                        if (name.isNotBlank()) doc["name"] = name
                        if (language.isNotBlank()) doc["language"] = language
                        
                        db.collection("users").document(user.uid).get().addOnSuccessListener { snap ->
                           if (!snap.exists()) {
                               doc["role"] = "user"
                               doc["createdAt"] = FieldValue.serverTimestamp()
                           }
                           
                           db.collection("users").document(user.uid)
                               .set(doc, SetOptions.merge())
                               .addOnSuccessListener { done(true, "Inicio de sesión exitoso") }
                               .addOnFailureListener { e -> done(false, "Fallo al actualizar perfil: ${e.localizedMessage}") }
                        }
                    } else {
                        done(false, "Usuario no disponible.")
                    }
                } else {
                    done(false, task.exception?.localizedMessage ?: "Error al verificar código")
                }
            }
    }

    fun signOut() = auth.signOut()

    fun getCurrentUserRole(done: (ok: Boolean, role: String?, message: String) -> Unit) {
        val uid = auth.currentUser?.uid ?: return done(false, null, "Sin usuario.")
        db.collection("users").document(uid).get()
            .addOnSuccessListener { snap ->
                val role = snap.getString("role") ?: "user"
                done(true, role, "Rol obtenido")
            }
            .addOnFailureListener { e -> done(false, null, e.localizedMessage ?: "Error al obtener rol") }
    }
    
    fun updateLanguage(newLanguage: String, done: (ok: Boolean, message: String) -> Unit) {
        val uid = auth.currentUser?.uid ?: return done(false, "Sin usuario.")
        db.collection("users").document(uid)
            .set(mapOf("language" to newLanguage, "updatedAt" to FieldValue.serverTimestamp()), SetOptions.merge())
            .addOnSuccessListener { done(true, "Idioma actualizado.") }
            .addOnFailureListener { done(false, it.localizedMessage ?: "Error") }
    }

    fun isCurrentUserAdmin(): Boolean {
        val user = auth.currentUser ?: return false
        return user.email == "admin@textmemail.com" || user.phoneNumber == ADMIN_PHONE
    }

    companion object {
        const val ADMIN_PHONE = "+34600000000"
    }
    
    fun deleteUserFromFirestore(uid: String, done: (ok: Boolean, message: String) -> Unit) {
        db.collection("users").document(uid).delete()
            .addOnSuccessListener { done(true, "Eliminado") }
            .addOnFailureListener { done(false, it.localizedMessage ?: "Error") }
    }
}
