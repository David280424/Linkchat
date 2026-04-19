// app/src/main/java/com/example/textmemail/MainActivity.kt
package com.example.textmemail

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

            BackHandler(enabled = isLoggedIn && (selectedContact != null || showContacts || showAdmin || showSettings)) {
                if (selectedContact != null) {
                    selectedContact = null
                } else if (showContacts) {
                    showContacts = false
                } else if (showAdmin) {
                    showAdmin = false
                } else if (showSettings) {
                    showSettings = false
                }
            }

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
                                    Contact(uid = doc.id, name = name, email = displayId)
                                } else null
                            }
                        }
                    }
                }
            }

            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color(0xFF673AB7),
                    secondary = Color(0xFF00BFA5),
                    background = Color(0xFFF5F5F7)
                )
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navigationState = when {
                        !isLoggedIn -> "auth"
                        showAdmin -> "admin"
                        showSettings -> "settings"
                        selectedContact != null -> "chat"
                        showContacts -> "contacts"
                        else -> "home"
                    }

                    AnimatedContent(
                        targetState = navigationState,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
                        },
                        label = "MainNavigation"
                    ) { state ->
                        when (state) {
                            "auth" -> AuthPhoneScreen(
                                onSendCode = { phone, callback ->
                                    // CORREGIDO: Coincide con PhoneAuthManager.sendVerificationCode(activity, phone, callback)
                                    phoneAuth.sendVerificationCode(this@MainActivity, phone) { success, errorMsg ->
                                        if (success) {
                                            Toast.makeText(this@MainActivity, "Código enviado", Toast.LENGTH_SHORT).show()
                                        }
                                        callback(success, errorMsg)
                                    }
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
                                onLanguageChanged = { lang -> applyLanguage(lang, recreate = false) }
                            )
                            "admin" -> AdminScreen(
                                users = contacts,
                                onDeleteUser = { contact, cb -> phoneAuth.deleteUserFromFirestore(contact.uid, cb) },
                                onBack = { showAdmin = false }
                            )
                            "settings" -> SettingsScreen(
                                currentLanguage = currentLanguage,
                                onSave = { lang ->
                                    phoneAuth.updateLanguage(lang) { _, _ -> }
                                    applyLanguage(lang, recreate = true)
                                },
                                onClose = { showSettings = false }
                            )
                            "contacts" -> ContactsScreen(
                                contacts = contacts,
                                onBack = { showContacts = false },
                                onOpenChat = { contact -> selectedContact = contact }
                            )
                            "chat" -> {
                                val chatTarget = remember { selectedContact }
                                if (chatTarget != null) {
                                    ChatScreen(
                                        contact = chatTarget,
                                        allContacts = contacts,
                                        onBack = { selectedContact = null }
                                    )
                                }
                            }
                            else -> HomeScreen(
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
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFFEDE7F6), Color(0xFFF5F5F7))
                )
            )
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
        ) {
            Icon(
                imageVector = Icons.Default.AccountCircle,
                contentDescription = null,
                modifier = Modifier.size(100.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Text(stringResource(R.string.welcome), style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(identifier.ifBlank { "Usuario" }, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                    Text("${stringResource(R.string.role)}: ${role.replaceFirstChar { it.uppercase() }}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
            }

            Spacer(Modifier.height(8.dp))

            HomeButton(stringResource(R.string.contacts_chat), Icons.Default.Chat, onOpenContacts, MaterialTheme.colorScheme.primary)
            HomeButton(stringResource(R.string.settings), Icons.Default.Settings, onOpenSettings, MaterialTheme.colorScheme.secondary)
            
            if (onOpenAdmin != null) {
                HomeButton(stringResource(R.string.admin_panel), Icons.Default.AdminPanelSettings, onOpenAdmin, Color(0xFFE53935))
            }

            TextButton(onClick = onSignOut) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.sign_out), color = Color.Gray)
                }
            }
        }
    }
}

@Composable
fun HomeButton(text: String, icon: ImageVector, onClick: () -> Unit, color: Color) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Text(text, fontWeight = FontWeight.SemiBold)
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

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back), modifier = Modifier.size(24.dp)) }
            Text(stringResource(R.string.settings), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        
        Spacer(Modifier.height(24.dp))
        
        Text(stringResource(R.string.app_language), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = lang == "es",
                onClick = { lang = "es" },
                label = { Text(stringResource(R.string.spanish)) },
                shape = RoundedCornerShape(12.dp)
            )
            FilterChip(
                selected = lang == "en",
                onClick = { lang = "en" },
                label = { Text(stringResource(R.string.english)) },
                shape = RoundedCornerShape(12.dp)
            )
        }

        Spacer(Modifier.weight(1f))
        
        Button(
            onClick = { onSave(lang) },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(stringResource(R.string.save_changes))
        }
    }
}
