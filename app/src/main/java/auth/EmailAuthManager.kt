package auth

import com.google.firebase.auth.*
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

data class UserProfile(
    val name: String,
    val language: String, // "es" | "en"
    val email: String,
    val role: String = "user"
)

class EmailAuthManager(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    /** Crea usuario en Auth, envía verificación y guarda perfil en Firestore */
    fun register(
        name: String,
        email: String,
        password: String,
        language: String,
        done: (ok: Boolean, message: String) -> Unit
    ) {
        if (!email.isValidEmail() || password.length < 6 || name.isBlank()) {
            done(false, "Datos inválidos. Revisa nombre, correo y contraseña (≥6).")
            return
        }

        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { t ->
                if (!t.isSuccessful) {
                    val msg = when (val e = t.exception) {
                        is FirebaseAuthUserCollisionException -> "El correo ya está registrado."
                        is FirebaseAuthInvalidCredentialsException -> "Correo inválido."
                        else -> e?.localizedMessage ?: "No se pudo crear la cuenta."
                    }
                    done(false, msg)
                    return@addOnCompleteListener
                }

                val user = auth.currentUser
                if (user == null) {
                    done(false, "Usuario no disponible tras registro.")
                    return@addOnCompleteListener
                }

                // Enviar verificación
                user.sendEmailVerification()

                // Guardar perfil en Firestore bajo /users/{uid}
                val doc = mapOf(
                    "name" to name,
                    "language" to language,
                    "email" to email,
                    "role" to "user",
                    "isEmailVerified" to false, // Se marcará como true cuando verifique
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                db.collection("users").document(user.uid)
                    .set(doc, SetOptions.merge()) // <- asegura merge si ya existe
                    .addOnSuccessListener {
                        done(true, "Cuenta creada. Revisa tu correo para verificar.")
                    }
                    .addOnFailureListener { e ->
                        done(false, "Cuenta creada, pero fallo al crear perfil: ${e.localizedMessage}")
                    }
            }
    }

    /** Login simple por email/password */
    fun login(
        email: String,
        password: String,
        done: (ok: Boolean, message: String) -> Unit
    ) {
        if (!email.isValidEmail() || password.isBlank()) {
            done(false, "Revisa correo y contraseña.")
            return
        }
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { t ->
                if (t.isSuccessful) {
                    // Asegura que el perfil esté creado/actualizado
                    val user = auth.currentUser
                    if (user != null) {
                        db.collection("users").document(user.uid)
                            .set(
                                mapOf(
                                    "email" to user.email,
                                    "isEmailVerified" to user.isEmailVerified, // Actualizar estado de verificación
                                    "updatedAt" to FieldValue.serverTimestamp()
                                ), SetOptions.merge()
                            )
                    }
                    done(true, "Inicio de sesión OK.")
                } else {
                    val msg = when (val e = t.exception) {
                        is FirebaseAuthInvalidUserException -> "Usuario no existe."
                        is FirebaseAuthInvalidCredentialsException -> "Contraseña incorrecta."
                        else -> e?.localizedMessage ?: "No se pudo iniciar sesión."
                    }
                    done(false, msg)
                }
            }
    }

    /** Obtener rol actual desde Firestore */
    fun getCurrentUserRole(done: (ok: Boolean, role: String?, message: String) -> Unit) {
        val uid = auth.currentUser?.uid ?: return done(false, null, "Sin usuario.")
        db.collection("users").document(uid).get()
            .addOnSuccessListener { snap ->
                if (snap.exists()) {
                    val role = snap.getString("role") ?: "user"
                    done(true, role, "Rol obtenido: $role")
                } else {
                    done(false, null, "Perfil no encontrado.")
                }
            }
            .addOnFailureListener { e ->
                done(false, null, e.localizedMessage ?: "Error al leer rol.")
            }
    }

    /** Reenviar verificación de email */
    fun resendVerification(done: (ok: Boolean, message: String) -> Unit) {
        val user = auth.currentUser
        if (user == null) { done(false, "Sin usuario activo."); return }
        user.sendEmailVerification()
            .addOnSuccessListener { done(true, "Correo de verificación reenviado.") }
            .addOnFailureListener { e -> done(false, e.localizedMessage ?: "No se pudo reenviar.") }
    }

    /** Refresca el usuario y dice si ya está verificado */
    fun reloadAndIsVerified(done: (verified: Boolean) -> Unit) {
        val user = auth.currentUser ?: return done(false)
        user.reload().addOnCompleteListener {
            val isVerified = auth.currentUser?.isEmailVerified == true
            
            // Actualizar estado en Firestore si está verificado
            if (isVerified) {
                db.collection("users").document(user.uid)
                    .set(
                        mapOf(
                            "isEmailVerified" to true,
                            "updatedAt" to FieldValue.serverTimestamp()
                        ), SetOptions.merge()
                    )
            }
            
            done(isVerified)
        }
    }

    /** Cambiar idioma en Firestore */
    fun updateLanguage(newLanguage: String, done: (ok: Boolean, message: String) -> Unit) {
        val uid = auth.currentUser?.uid ?: return done(false, "Sin usuario.")

        val data = mapOf(
            "language" to newLanguage,
            "updatedAt" to FieldValue.serverTimestamp()
        )

        db.collection("users").document(uid)
            .set(data, SetOptions.merge())
            .addOnSuccessListener { done(true, "Idioma actualizado.") }
            .addOnFailureListener { e ->
                done(false, e.localizedMessage ?: "No se pudo actualizar idioma.")
            }
    }

    /** Cambiar email en Auth y Firestore */
    fun updateEmail(newEmail: String, done: (ok: Boolean, message: String) -> Unit) {
        val user = auth.currentUser ?: return done(false, "Sin usuario.")
        if (!newEmail.isValidEmail()) { done(false, "Correo inválido."); return }
        user.updateEmail(newEmail)
            .addOnSuccessListener {
                db.collection("users").document(user.uid)
                    .set(
                        mapOf(
                            "email" to newEmail,
                            "updatedAt" to FieldValue.serverTimestamp()
                        ),
                        SetOptions.merge()
                    )
                done(true, "Correo actualizado.")
            }
            .addOnFailureListener { e ->
                done(false, e.localizedMessage ?: "No se pudo actualizar correo (puede requerir reautenticación).")
            }
    }

    /** Enviar email para restablecer contraseña */
    fun sendPasswordReset(email: String, done: (ok: Boolean, message: String) -> Unit) {
        if (!email.isValidEmail()) { done(false, "Correo inválido."); return }
        auth.sendPasswordResetEmail(email)
            .addOnSuccessListener { done(true, "Email de restablecimiento enviado.") }
            .addOnFailureListener { e -> done(false, e.localizedMessage ?: "No se pudo enviar reset.") }
    }

    fun signOut() = auth.signOut()

    /** Migrar usuarios existentes sin el campo isEmailVerified */
    fun migrateExistingUser(done: (ok: Boolean, message: String) -> Unit) {
        val user = auth.currentUser ?: return done(false, "Sin usuario.")
        
        db.collection("users").document(user.uid).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val isEmailVerified = doc.getBoolean("isEmailVerified")
                    if (isEmailVerified == null) {
                        // Usuario sin el campo, actualizarlo
                        db.collection("users").document(user.uid)
                            .set(
                                mapOf(
                                    "isEmailVerified" to user.isEmailVerified,
                                    "updatedAt" to FieldValue.serverTimestamp()
                                ), SetOptions.merge()
                            )
                            .addOnSuccessListener { 
                                done(true, "Usuario migrado correctamente.")
                            }
                            .addOnFailureListener { e ->
                                done(false, "Error migrando usuario: ${e.localizedMessage}")
                            }
                    } else {
                        done(true, "Usuario ya tiene el campo actualizado.")
                    }
                } else {
                    done(false, "Perfil de usuario no encontrado.")
                }
            }
            .addOnFailureListener { e ->
                done(false, "Error verificando usuario: ${e.localizedMessage}")
            }
    }
}

private fun String.isValidEmail(): Boolean =
    isNotBlank() && android.util.Patterns.EMAIL_ADDRESS.matcher(this).matches()