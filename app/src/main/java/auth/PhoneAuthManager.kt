package auth

import android.app.Activity
import android.util.Log
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

    /** Acceso rápido para el administrador (Bypass de SMS) */
    fun quickAdminLogin(done: (ok: Boolean, message: String) -> Unit) {
        val adminEmail = "admin@textmemail.com"
        val adminPass = "admin123456"

        auth.signInWithEmailAndPassword(adminEmail, adminPass)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    if (user != null) {
                        val doc = mutableMapOf(
                            "email" to adminEmail,
                            "name" to "Administrador",
                            "role" to "admin",
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                        db.collection("users").document(user.uid)
                            .set(doc, SetOptions.merge())
                            .addOnSuccessListener { done(true, "Acceso Admin Correcto") }
                            .addOnFailureListener { e -> done(false, "Error Firestore: ${e.localizedMessage}") }
                    }
                } else {
                    val e = task.exception
                    val errorMsg = when {
                        e?.message?.contains("chain validation failed") == true -> 
                            "ERROR DE RED: Revisa que la FECHA Y HORA de tu celular sean correctas y que tengas internet."
                        else -> e?.localizedMessage ?: "Error desconocido"
                    }
                    done(false, errorMsg)
                }
            }
    }

    /** Envía el código de verificación al teléfono */
    fun sendVerificationCode(
        activity: Activity,
        phoneNumber: String,
        onCodeSent: (Boolean, String?) -> Unit
    ) {
        val finalPhone = if (phoneNumber.startsWith("+")) phoneNumber else if (phoneNumber.length == 10) "+52$phoneNumber" else phoneNumber

        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(finalPhone)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(credential: PhoneAuthCredential) {}
                override fun onVerificationFailed(e: FirebaseException) {
                    val msg = when {
                        e.message?.contains("billing") == true -> "ERROR: Facturación requerida para SMS reales. USA NÚMEROS DE PRUEBA."
                        else -> e.localizedMessage
                    }
                    onCodeSent(false, msg)
                }
                override fun onCodeSent(id: String, token: PhoneAuthProvider.ForceResendingToken) {
                    this@PhoneAuthManager.verificationId = id
                    onCodeSent(true, null)
                }
            })
            .build()
        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    /** Verifica el código ingresado */
    fun verifyCode(code: String, name: String, language: String, done: (ok: Boolean, message: String) -> Unit) {
        val id = verificationId ?: return done(false, "Primero envía el código.")
        val credential = PhoneAuthProvider.getCredential(id, code)
        auth.signInWithCredential(credential).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val user = auth.currentUser
                if (user != null) {
                    db.collection("users").document(user.uid).get().addOnSuccessListener { snap ->
                        val doc = mutableMapOf<String, Any>(
                            "phone" to (user.phoneNumber ?: ""),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                        
                        // Si el número es el teléfono maestro del Admin, le damos el rol de admin automáticamente
                        val isMasterAdminPhone = user.phoneNumber == ADMIN_PHONE

                        if (!snap.exists()) {
                            doc["name"] = if (isMasterAdminPhone) "Admin Master" else name
                            doc["language"] = language
                            doc["role"] = if (isMasterAdminPhone) "admin" else "user"
                            doc["createdAt"] = FieldValue.serverTimestamp()
                        } else {
                            if (name.isNotBlank()) doc["name"] = name
                            if (language.isNotBlank()) doc["language"] = language
                            if (isMasterAdminPhone) doc["role"] = "admin"
                        }

                        db.collection("users").document(user.uid).set(doc, SetOptions.merge())
                            .addOnSuccessListener { done(true, "¡Bienvenido!") }
                    }
                }
            } else {
                done(false, "Código incorrecto.")
            }
        }
    }

    fun updateLanguage(newLanguage: String, done: (ok: Boolean, message: String) -> Unit) {
        val uid = auth.currentUser?.uid ?: return done(false, "Sin sesión.")
        db.collection("users").document(uid)
            .set(mapOf("language" to newLanguage, "updatedAt" to FieldValue.serverTimestamp()), SetOptions.merge())
            .addOnSuccessListener { done(true, "Idioma actualizado.") }
            .addOnFailureListener { e -> done(false, e.localizedMessage ?: "Error") }
    }

    fun signOut() = auth.signOut()

    fun getCurrentUserRole(done: (ok: Boolean, role: String?, message: String) -> Unit) {
        val uid = auth.currentUser?.uid ?: return done(false, null, "No hay sesión.")
        db.collection("users").document(uid).get()
            .addOnSuccessListener { snap ->
                val role = snap.getString("role") ?: "user"
                done(true, role, "OK")
            }
            .addOnFailureListener { e -> done(false, null, e.localizedMessage ?: "Error") }
    }

    fun isCurrentUserAdmin(): Boolean {
        val user = auth.currentUser ?: return false
        return user.email == "admin@textmemail.com" || user.email == "admin@linkchat.com" || user.phoneNumber == ADMIN_PHONE
    }

    companion object {
        const val ADMIN_PHONE = "+521010101010" // Tu nuevo teléfono especial de administrador
    }

    fun deleteUserFromFirestore(uid: String, done: (ok: Boolean, message: String) -> Unit) {
        db.collection("users").document(uid).delete().addOnSuccessListener { done(true, "Eliminado") }
    }
}
