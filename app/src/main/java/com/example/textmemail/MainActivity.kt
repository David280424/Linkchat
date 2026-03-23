// app/src/main/java/com/example/textmemail/MainActivity.kt
package com.example.textmemail

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import auth.PhoneAuthManager
import com.example.textmemail.ui_auth.AdminScreen
import com.example.textmemail.ui_auth.AuthPhoneScreen
import com.example.textmemail.ui_chat.ChatScreen
import com.example.textmemail.ui_chat.ContactsScreen
import com.example.textmemail.models.Contact
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.Locale

// ---------- DataStore (idioma) ----------
private val Context.dataStore by preferencesDataStore(name = "settings")
private val KEY_LANG = stringPreferencesKey("language")
private const val DEFAULT_LANG = "es"

private fun setAppLocale(activity: ComponentActivity, langTag: String) {
    val locale = Locale.forLanguageTag(langTag)
    Locale.setDefault(locale)
    val cfg = activity.resources.configuration
    cfg.setLocale(locale)
    activity.createConfigurationContext(cfg)
    activity.resources.updateConfiguration(cfg, activity.resources.displayMetrics)
}

class MainActivity : ComponentActivity() {

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val phoneAuth by lazy { PhoneAuthManager(auth, FirebaseFirestore.getInstance()) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            var isLoggedIn by remember { mutableStateOf(auth.currentUser != null) }
            var currentIdentifier by remember { mutableStateOf(auth.currentUser?.phoneNumber ?: auth.currentUser?.email ?: "") }

            var currentLanguage by remember { mutableStateOf(DEFAULT_LANG) }
            var currentRole by remember { mutableStateOf("user") }

            var showSettings by remember { mutableStateOf(false) }
            var showAdmin by remember { mutableStateOf(false) }
            var showContacts by remember { mutableStateOf(false) }
            var selectedContact by remember { mutableStateOf<Contact?>(null) }

            var contacts by remember { mutableStateOf(listOf<Contact>()) }

            val db = FirebaseFirestore.getInstance()

            // Observa DataStore
            LaunchedEffect(Unit) {
                lifecycleScope.launch {
                    repeatOnLifecycle(Lifecycle.State.STARTED) {
                        applicationContext.dataStore.data
                            .map { it[KEY_LANG] ?: DEFAULT_LANG }
                            .collect { lang ->
                                setAppLocale(this@MainActivity, lang)
                                currentLanguage = lang
                            }
                    }
                }
            }

            // Listener de Auth
            DisposableEffect(Unit) {
                val l = FirebaseAuth.AuthStateListener { fa ->
                    val u = fa.currentUser
                    isLoggedIn = u != null
                    currentIdentifier = u?.phoneNumber ?: u?.email ?: ""
                    if (u == null) {
                        showSettings = false
                        showContacts = false
                        selectedContact = null
                        currentRole = "user"
                    }
                }
                auth.addAuthStateListener(l)
                onDispose { auth.removeAuthStateListener(l) }
            }

            // Cargar rol y contactos
            LaunchedEffect(isLoggedIn) {
                if (isLoggedIn) {
                    phoneAuth.getCurrentUserRole { ok, role, _ ->
                        currentRole = if (ok && !role.isNullOrBlank()) role!! else "user"
                    }

                    db.collection("users").addSnapshotListener { snaps, _ ->
                        if (snaps != null) {
                            contacts = snaps.documents.mapNotNull { doc ->
                                val phone = doc.getString("phone")
                                val email = doc.getString("email")
                                val name = doc.getString("name") ?: ""
                                val displayId = phone ?: email

                                if (!displayId.isNullOrBlank()) {
                                    Contact(
                                        uid = doc.id,
                                        name = name,
                                        email = displayId // Usamos el campo email del modelo para el identificador
                                    )
                                } else null
                            }
                        }
                    }
                } else {
                    currentRole = "user"
                    contacts = emptyList()
                }
            }

            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    when {
                        !isLoggedIn -> {
                            AuthPhoneScreen(
                                onSendCode = { phone ->
                                    phoneAuth.sendVerificationCode(
                                        activity = this@MainActivity,
                                        phoneNumber = phone,
                                        onCodeSent = { 
                                            Toast.makeText(this@MainActivity, "Código enviado", Toast.LENGTH_SHORT).show()
                                        },
                                        onError = { error ->
                                            Toast.makeText(this@MainActivity, error, Toast.LENGTH_LONG).show()
                                        }
                                    )
                                },
                                onVerifyCode = { code, name, lang, done ->
                                    phoneAuth.verifyCode(code, name, lang) { ok, msg ->
                                        if (ok) applyLanguage(lang, recreate = true)
                                        done(ok, msg)
                                    }
                                },
                                onQuickAdminLogin = { done ->
                                    phoneAuth.quickAdminLogin { ok, msg ->
                                        if (ok) applyLanguage(DEFAULT_LANG, recreate = true)
                                        done(ok, msg)
                                    }
                                },
                                onLanguageChanged = { lang ->
                                    applyLanguage(lang, recreate = false)
                                }
                            )
                        }
                        else -> {
                            when {
                                showAdmin -> {
                                    AdminScreen(
                                        users = contacts,
                                        onDeleteUser = { contact, cb ->
                                            phoneAuth.deleteUserFromFirestore(contact.uid, cb)
                                        },
                                        onBack = { showAdmin = false }
                                    )
                                }
                                showSettings -> {
                                    SettingsScreen(
                                        currentLanguage = currentLanguage,
                                        onSave = { lang: String ->
                                            phoneAuth.updateLanguage(lang) { _, _ -> }
                                            applyLanguage(lang, recreate = true)
                                        },
                                        onClose = { showSettings = false }
                                    )
                                }
                                showContacts -> {
                                    ContactsScreen(
                                        contacts = contacts,
                                        onBack = { showContacts = false },
                                        onOpenChat = { contact ->
                                            selectedContact = contact
                                            showContacts = false
                                        }
                                    )
                                }
                                selectedContact != null -> {
                                    ChatScreen(
                                        contact = selectedContact!!,
                                        onBack = { selectedContact = null }
                                    )
                                }
                                else -> {
                                    HomeScreen(
                                        identifier = currentIdentifier,
                                        role = currentRole,
                                        onOpenSettings = { showSettings = true },
                                        onSignOut = { phoneAuth.signOut() },
                                        onOpenContacts = { showContacts = true },
                                        onOpenAdmin = if (phoneAuth.isCurrentUserAdmin()) {{ showAdmin = true }} else null
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun applyLanguage(lang: String, recreate: Boolean) {
        lifecycleScope.launch {
            applicationContext.dataStore.edit { it[KEY_LANG] = lang }
            setAppLocale(this@MainActivity, lang)
            if (recreate) this@MainActivity.recreate()
        }
    }
}

@Composable
private fun HomeScreen(
    identifier: String,
    role: String,
    onOpenSettings: () -> Unit,
    onSignOut: () -> Unit,
    onOpenContacts: () -> Unit,
    onOpenAdmin: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Sesión iniciada", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(if (identifier.isNotBlank()) identifier else "(—)")
        Spacer(Modifier.height(8.dp))
        Text("Rol: ${role.ifBlank { "user" }}")
        Spacer(Modifier.height(24.dp))

        OutlinedButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
            Text("Ajustes")
        }
        Spacer(Modifier.height(12.dp))
        Button(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) {
            Text("Cerrar sesión")
        }
        Spacer(Modifier.height(12.dp))
        Button(onClick = onOpenContacts, modifier = Modifier.fillMaxWidth()) {
            Text("Contactos")
        }
        if (onOpenAdmin != null) {
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onOpenAdmin,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color.Red)
            ) {
                Text("Panel de Administrador")
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    currentLanguage: String,
    onSave: (String) -> Unit,
    onClose: () -> Unit
) {
    var lang by remember { mutableStateOf(currentLanguage) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Ajustes", style = MaterialTheme.typography.headlineSmall)

        Text("Idioma")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilterChip(selected = lang == "es", onClick = { lang = "es" }, label = { Text("ES") })
            FilterChip(selected = lang == "en", onClick = { lang = "en" }, label = { Text("EN") })
        }

        Button(onClick = { onSave(lang) }, modifier = Modifier.fillMaxWidth()) {
            Text("Guardar")
        }
        TextButton(onClick = onClose) {
            Text("Volver")
        }
    }
}
