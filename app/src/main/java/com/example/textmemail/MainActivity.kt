// app/src/main/java/com/example/textmemail/MainActivity.kt
package com.example.textmemail

import android.content.Context
import android.os.Bundle
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
import auth.EmailAuthManager
import com.example.textmemail.ui_auth.AuthEmailScreen
import com.example.textmemail.ui_auth.VerifyAndManageScreen
import com.example.textmemail.ui_chat.ChatScreen
import com.example.textmemail.ui_chat.ContactsScreen
import com.example.textmemail.models.Contact
import com.example.textmemail.models.Message
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
    private val emailAuth by lazy { EmailAuthManager(auth, FirebaseFirestore.getInstance()) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            var isLoggedIn by remember { mutableStateOf(auth.currentUser != null) }
            var isVerified by remember { mutableStateOf(true) } // Verificacion desactivada
            var currentEmail by remember { mutableStateOf(auth.currentUser?.email ?: "") }

            var currentLanguage by remember { mutableStateOf(DEFAULT_LANG) }
            var currentRole by remember { mutableStateOf("user") }

            var showSettings by remember { mutableStateOf(false) }
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
                    isVerified = true // Verificacion desactivada
                    currentEmail = u?.email ?: ""
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

            LaunchedEffect(isLoggedIn, isVerified) {
                if (isLoggedIn && isVerified) {
                    emailAuth.getCurrentUserRole { ok, role, _ ->
                        currentRole = if (ok && !role.isNullOrBlank()) role!! else "user"
                    }

                    db.collection("users").addSnapshotListener { snaps, _ ->
                        if (snaps != null) {
                            contacts = snaps.documents.mapNotNull { doc ->
                                val email = doc.getString("email")
                                val name = doc.getString("name") ?: ""

                                // SIN FILTROS - Todos los usuarios con email válido
                                if (!email.isNullOrBlank()) {
                                    Contact(
                                        uid = doc.id,
                                        name = name,
                                        email = email
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
                            AuthEmailScreen(
                                onRegister = { name, email, pass, lang, done ->
                                    emailAuth.register(name, email, pass, lang) { ok, msg ->
                                        if (ok) {
                                            applyLanguage(lang, recreate = true)
                                        }
                                        done(ok, msg)
                                    }
                                },
                                onLogin = { email, pass, done ->
                                    emailAuth.login(email, pass) { ok, msg -> done(ok, msg) }
                                },
                                onLanguageChanged = { lang ->
                                    applyLanguage(lang, recreate = false)
                                }
                            )
                        }
                        isLoggedIn && !isVerified -> {
                            VerifyAndManageScreen(
                                currentEmail = currentEmail,
                                currentLanguage = currentLanguage,
                                onResendVerification = { cb -> emailAuth.resendVerification(cb) },
                                onCheckVerified = { cb ->
                                    emailAuth.reloadAndIsVerified { verified ->
                                        isVerified = verified
                                        cb(verified)
                                    }
                                },
                                onUpdateLanguage = { newLang, cb ->
                                    emailAuth.updateLanguage(newLang) { ok, msg ->
                                        if (ok) applyLanguage(newLang, recreate = true)
                                        cb(ok, msg)
                                    }
                                },
                                onUpdateEmail = { newEmail, cb ->
                                    emailAuth.updateEmail(newEmail) { ok, msg ->
                                        if (ok) currentEmail = newEmail
                                        cb(ok, msg)
                                    }
                                },
                                onPasswordReset = { email, cb -> emailAuth.sendPasswordReset(email, cb) },
                                onSignOut = { emailAuth.signOut() }
                            )
                        }
                        else -> {
                            when {
                                showSettings -> {
                                    SettingsScreen(
                                        currentLanguage = currentLanguage,
                                        onSave = { lang: String ->
                                            emailAuth.updateLanguage(lang) { _, _ -> }
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
                                        email = currentEmail,
                                        role = currentRole,
                                        onOpenSettings = { showSettings = true },
                                        onSignOut = { emailAuth.signOut() },
                                        onOpenContacts = { showContacts = true }
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
    email: String,
    role: String,
    onOpenSettings: () -> Unit,
    onSignOut: () -> Unit,
    onOpenContacts: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(stringResource(R.string.signed_in_verified), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(if (email.isNotBlank()) email else "(—)")
        Spacer(Modifier.height(8.dp))
        Text("${stringResource(R.string.role)}: ${role.ifBlank { "user" }}")
        Spacer(Modifier.height(24.dp))

        OutlinedButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.settings))
        }
        Spacer(Modifier.height(12.dp))
        Button(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.sign_out))
        }
        Spacer(Modifier.height(12.dp))
        Button(onClick = onOpenContacts, modifier = Modifier.fillMaxWidth()) {
            Text("Contactos")
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
        Text(stringResource(R.string.settings), style = MaterialTheme.typography.headlineSmall)

        Text(stringResource(R.string.language))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilterChip(selected = lang == "es", onClick = { lang = "es" }, label = { Text("ES") })
            FilterChip(selected = lang == "en", onClick = { lang = "en" }, label = { Text("EN") })
        }

        Button(onClick = { onSave(lang) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.save))
        }
        TextButton(onClick = onClose) {
            Text(stringResource(R.string.back))
        }
    }
}