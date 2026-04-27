// app/src/main/java/com/example/textmemail/MainActivity.kt
package com.example.textmemail

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import auth.PhoneAuthManager
import auth.ChatManager
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

/** Lee contactos del teléfono */
fun fetchPhoneContacts(context: Context): List<Contact> {
    val contactList = mutableListOf<Contact>()
    val cursor = context.contentResolver.query(
        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
        null, null, null, null
    )
    cursor?.use {
        val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
        val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
        while (it.moveToNext()) {
            val name = it.getString(nameIndex)
            val number = it.getString(numberIndex)
            if (!number.isNullOrBlank()) {
                contactList.add(Contact(
                    uid = ChatManager.normalizePhone(number), 
                    name = name ?: "Sin nombre", 
                    email = number, // Usamos el campo email para mostrar el número
                    isOnline = false
                ))
            }
        }
    }
    return contactList.distinctBy { it.uid }
}

sealed class NavDestination {
    object Auth : NavDestination()
    object Home : NavDestination()
    object Admin : NavDestination()
    object Settings : NavDestination()
    object Contacts : NavDestination()
    data class Chat(val contact: Contact) : NavDestination()
}

class MainActivity : ComponentActivity() {

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val phoneAuth by lazy { PhoneAuthManager(auth, FirebaseFirestore.getInstance()) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val context = LocalContext.current
            var isLoggedIn by remember { mutableStateOf(auth.currentUser != null) }
            var currentIdentifier by remember { mutableStateOf(auth.currentUser?.phoneNumber ?: auth.currentUser?.email ?: "") }
            var currentLanguage by remember { mutableStateOf(DEFAULT_LANG) }
            var currentRole by remember { mutableStateOf("user") }

            var showSettings by remember { mutableStateOf(false) }
            var showAdmin by remember { mutableStateOf(false) }
            var showContacts by remember { mutableStateOf(false) }
            var selectedContact by remember { mutableStateOf<Contact?>(null) }
            
            var firebaseContacts by remember { mutableStateOf(listOf<Contact>()) }
            var phoneContacts by remember { mutableStateOf(listOf<Contact>()) }
            
            // Unimos ambas listas de contactos (solo si NO es admin)
            val allContacts = remember(firebaseContacts, phoneContacts, currentRole) {
                if (currentRole == "admin") {
                    firebaseContacts.distinctBy { it.uid }
                } else {
                    (firebaseContacts + phoneContacts).distinctBy { it.uid }
                }
            }

            var globalIncomingCall by remember { mutableStateOf<Pair<String, String>?>(null) }
            val db = FirebaseFirestore.getInstance()

            // Lanzador de permisos
            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { perms ->
                if (perms[Manifest.permission.READ_CONTACTS] == true) {
                    phoneContacts = fetchPhoneContacts(context)
                }
            }

            // Solo solicita permisos si NO es admin
            LaunchedEffect(isLoggedIn, currentRole) {
                if (isLoggedIn && currentRole != "admin") {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
                        permissionLauncher.launch(arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.CALL_PHONE))
                    } else {
                        phoneContacts = fetchPhoneContacts(context)
                    }
                } else if (currentRole == "admin") {
                    phoneContacts = emptyList() // El admin nunca ve la agenda del teléfono
                }
            }

            BackHandler(enabled = isLoggedIn && (selectedContact != null || showContacts || showAdmin || showSettings)) {
                if (selectedContact != null) selectedContact = null
                else if (showContacts) showContacts = false
                else if (showAdmin) showAdmin = false
                else if (showSettings) showSettings = false
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

            LaunchedEffect(isLoggedIn) {
                if (isLoggedIn) {
                    phoneAuth.setUserOnlineStatus(true)
                    val myUid = auth.currentUser?.uid ?: return@LaunchedEffect
                    
                    // Obtener rol primero para saber si pedir contactos o no
                    phoneAuth.getCurrentUserRole { ok, role, _ ->
                        if (ok) currentRole = role ?: "user"
                    }

                    db.collection("chats").addSnapshotListener { snapshots, _ ->
                        snapshots?.documents?.forEach { doc ->
                            if (doc.id.contains(myUid)) {
                                val activeCall = doc.get("activeCall") as? Map<*, *>
                                val callerId = activeCall?.get("callerId") as? String
                                val channel = activeCall?.get("channelName") as? String
                                if (callerId != null && callerId != myUid) {
                                    globalIncomingCall = callerId to (channel ?: "")
                                } else if (activeCall == null) {
                                    globalIncomingCall = null
                                }
                            }
                        }
                    }
                    
                    // Escuchar usuarios de Firebase
                    db.collection("users").addSnapshotListener { snaps, _ ->
                        firebaseContacts = snaps?.documents?.mapNotNull { doc ->
                            if (doc.id != auth.currentUser?.uid) {
                                Contact(
                                    uid = doc.id,
                                    name = doc.getString("name") ?: "",
                                    email = doc.getString("phone") ?: doc.getString("email") ?: "",
                                    isOnline = doc.getBoolean("isOnline") ?: false
                                )
                            } else null
                        } ?: emptyList()
                    }
                }
            }

            DisposableEffect(Unit) {
                val l = FirebaseAuth.AuthStateListener { fa ->
                    isLoggedIn = fa.currentUser != null
                    currentIdentifier = fa.currentUser?.phoneNumber ?: fa.currentUser?.email ?: ""
                }
                auth.addAuthStateListener(l)
                onDispose { 
                    phoneAuth.setUserOnlineStatus(false)
                    auth.removeAuthStateListener(l) 
                }
            }

            MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF673AB7), secondary = Color(0xFF00BFA5))) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navDestination = when {
                        !isLoggedIn -> NavDestination.Auth
                        selectedContact != null -> NavDestination.Chat(selectedContact!!)
                        showAdmin -> NavDestination.Admin
                        showSettings -> NavDestination.Settings
                        showContacts -> NavDestination.Contacts
                        else -> NavDestination.Home
                    }

                    if (globalIncomingCall != null) {
                        AlertDialog(
                            onDismissRequest = { },
                            title = { Text("Llamada Entrante") },
                            text = { Text("Están intentando contactar contigo por video.") },
                            confirmButton = {
                                Button(onClick = {
                                    val intent = Intent(this@MainActivity, VideoCallActivity::class.java).apply {
                                        putExtra("CHANNEL_NAME", globalIncomingCall!!.second)
                                    }
                                    startActivity(intent)
                                    globalIncomingCall = null
                                }) { Text("ACEPTAR") }
                            },
                            dismissButton = {
                                TextButton(onClick = {
                                    ChatManager.endCallSignal(globalIncomingCall!!.first)
                                    globalIncomingCall = null
                                }) { Text("RECHAZAR", color = Color.Red) }
                            }
                        )
                    }

                    AnimatedContent(targetState = navDestination, label = "Navigation") { state ->
                        when (state) {
                            is NavDestination.Auth -> AuthPhoneScreen(
                                onSendCode = { phone, cb ->
                                    phoneAuth.sendVerificationCode(this@MainActivity, phone) { s, e -> cb(s, e) }
                                },
                                onVerifyCode = { c, n, l, done ->
                                    phoneAuth.verifyCode(c, n, l) { ok, msg -> if (ok) applyLanguage(l, true); done(ok, msg) }
                                },
                                onQuickAdminLogin = { done ->
                                    phoneAuth.quickAdminLogin { ok, msg -> if (ok) applyLanguage(DEFAULT_LANG, true); done(ok, msg) }
                                },
                                onLanguageChanged = { l -> applyLanguage(l, false) }
                            )
                            is NavDestination.Chat -> ChatScreen(contact = state.contact, allContacts = allContacts, onBack = { selectedContact = null })
                            is NavDestination.Admin -> AdminScreen(users = firebaseContacts, onDeleteUser = { c, cb -> phoneAuth.deleteUserFromFirestore(c.uid, cb) }, onBack = { showAdmin = false })
                            is NavDestination.Settings -> SettingsScreen(currentLanguage = currentLanguage, onSave = { l -> phoneAuth.updateLanguage(l) { _, _ -> }; applyLanguage(l, true) }, onClose = { showSettings = false })
                            is NavDestination.Contacts -> ContactsScreen(contacts = allContacts, onBack = { showContacts = false }, onOpenChat = { c -> selectedContact = c })
                            is NavDestination.Home -> HomeScreen(identifier = currentIdentifier, role = currentRole, onOpenSettings = { showSettings = true }, onSignOut = { phoneAuth.signOut() }, onOpenContacts = { showContacts = true }, onOpenAdmin = if (phoneAuth.isCurrentUserAdmin()) {{ showAdmin = true }} else null)
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
private fun HomeScreen(identifier: String, role: String, onOpenSettings: () -> Unit, onSignOut: () -> Unit, onOpenContacts: () -> Unit, onOpenAdmin: (() -> Unit)? = null) {
    Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFFEDE7F6), Color(0xFFF5F5F7))))) {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)) {
            Icon(Icons.Default.AccountCircle, null, Modifier.size(100.dp), tint = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.welcome), style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold))
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(identifier.ifBlank { "Usuario" }, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                }
            }
            HomeButton(stringResource(R.string.contacts_chat), Icons.Default.Chat, onOpenContacts, MaterialTheme.colorScheme.primary)
            HomeButton(stringResource(R.string.settings), Icons.Default.Settings, onOpenSettings, MaterialTheme.colorScheme.secondary)
            if (onOpenAdmin != null) HomeButton(stringResource(R.string.admin_panel), Icons.Default.AdminPanelSettings, onOpenAdmin, Color(0xFFE53935))
            TextButton(onClick = onSignOut) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Logout, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.sign_out), color = Color.Gray) } }
        }
    }
}

@Composable
fun HomeButton(text: String, icon: ImageVector, onClick: () -> Unit, color: Color) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = color)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, Modifier.size(24.dp)); Spacer(Modifier.width(12.dp)); Text(text, fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
private fun SettingsScreen(currentLanguage: String, onSave: (String) -> Unit, onClose: () -> Unit) {
    var lang by remember { mutableStateOf(currentLanguage) }
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = onClose) { Icon(Icons.Default.ArrowBack, null, Modifier.size(24.dp)) }; Text(stringResource(R.string.settings), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.height(24.dp)); Text(stringResource(R.string.app_language), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = lang == "es", onClick = { lang = "es" }, label = { Text(stringResource(R.string.spanish)) }, shape = RoundedCornerShape(12.dp))
            FilterChip(selected = lang == "en", onClick = { lang = "en" }, label = { Text(stringResource(R.string.english)) }, shape = RoundedCornerShape(12.dp))
        }
        Spacer(Modifier.weight(1f)); Button(onClick = { onSave(lang) }, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(12.dp)) { Text(stringResource(R.string.save_changes)) }
    }
}
