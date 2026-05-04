// app/src/main/java/com/example/textmemail/MainActivity.kt
package com.example.textmemail

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import ui_chat.ChatScreen
import ui_chat.ContactsScreen
import com.example.textmemail.models.Contact
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.Date
import java.text.SimpleDateFormat

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
                    email = number,
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
            var currentLanguage by remember { mutableStateOf(DEFAULT_LANG) }
            var currentRole by remember { mutableStateOf("user") }

            var showSettings by remember { mutableStateOf(false) }
            var showAdmin by remember { mutableStateOf(false) }
            var showContacts by remember { mutableStateOf(false) }
            var selectedContact by remember { mutableStateOf<Contact?>(null) }
            
            var firebaseContacts by remember { mutableStateOf(listOf<Contact>()) }
            var phoneContacts by remember { mutableStateOf(listOf<Contact>()) }
            
            val allContacts = remember(firebaseContacts, phoneContacts, currentRole) {
                if (currentRole == "admin") firebaseContacts.distinctBy { it.uid }
                else (firebaseContacts + phoneContacts).distinctBy { it.uid }
            }

            var globalIncomingCall by remember { mutableStateOf<Pair<String, String>?>(null) }
            var inAppNotification by remember { mutableStateOf<Pair<Contact, String>?>(null) }
            val db = FirebaseFirestore.getInstance()

            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { perms ->
                if (perms[Manifest.permission.READ_CONTACTS] == true) {
                    phoneContacts = fetchPhoneContacts(context)
                }
            }

            LaunchedEffect(isLoggedIn, currentRole) {
                if (isLoggedIn && currentRole != "admin") {
                    val perms = mutableListOf(Manifest.permission.READ_CONTACTS, Manifest.permission.CALL_PHONE)
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        perms.add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    permissionLauncher.launch(perms.toTypedArray())
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
                        applicationContext.dataStore.data.map { it[KEY_LANG] ?: DEFAULT_LANG }
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
                    
                    FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                        db.collection("users").document(myUid).update("fcmToken", token)
                    }

                    phoneAuth.getCurrentUserRole { ok, role, _ ->
                        if (ok) currentRole = role ?: "user"
                    }

                    // Listener para llamadas entrantes en cualquier chat
                    db.collection("chats")
                        .whereArrayContains("participants", myUid)
                        .addSnapshotListener { snapshots, _ ->
                            val callDoc = snapshots?.documents?.find { doc ->
                                val call = doc.get("activeCall") as? Map<*, *>
                                call?.get("status") == "ringing" && call?.get("callerId") != myUid
                            }
                            
                            if (callDoc != null) {
                                val call = callDoc.get("activeCall") as Map<*, *>
                                val cId = call["callerId"] as? String
                                val cName = call["channelName"] as? String
                                if (cId != null && cName != null) {
                                    globalIncomingCall = cId to cName
                                }
                            } else {
                                globalIncomingCall = null
                            }
                        }
                    
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

                    db.collectionGroup("messages")
                        .whereEqualTo("receiverId", myUid)
                        .whereEqualTo("isRead", false)
                        .orderBy("timestamp", Query.Direction.DESCENDING)
                        .limit(1)
                        .addSnapshotListener { snaps, _ ->
                            val msgDoc = snaps?.documents?.firstOrNull() ?: return@addSnapshotListener
                            val senderId = msgDoc.getString("senderId") ?: ""
                            val text = msgDoc.getString("text") ?: ""
                            if (selectedContact?.uid != senderId) {
                                val sender = firebaseContacts.find { it.uid == senderId } 
                                    ?: Contact(uid = senderId, name = "Mensaje Nuevo")
                                inAppNotification = sender to text
                            }
                        }
                }
            }

            MaterialTheme(colorScheme = lightColorScheme(
                primary = Color(0xFF673AB7), 
                secondary = Color(0xFF00BFA5),
                surfaceVariant = Color(0xFFF0F2F5)
            )) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navDestination = when {
                        !isLoggedIn -> NavDestination.Auth
                        selectedContact != null -> NavDestination.Chat(selectedContact!!)
                        showAdmin -> NavDestination.Admin
                        showSettings -> NavDestination.Settings
                        showContacts -> NavDestination.Contacts
                        else -> NavDestination.Home
                    }

                    Box(Modifier.fillMaxSize()) {
                        AnimatedContent(
                            targetState = navDestination, 
                            label = "Navigation",
                            transitionSpec = { fadeIn() togetherWith fadeOut() }
                        ) { state ->
                            when (state) {
                                is NavDestination.Auth -> AuthPhoneScreen(
                                    onSendCode = { phone, cb -> phoneAuth.sendVerificationCode(this@MainActivity, phone, cb) },
                                    onVerifyCode = { c, n, l, done -> phoneAuth.verifyCode(c, n, l) { ok, msg -> if (ok) { isLoggedIn = true; applyLanguage(l, true) }; done(ok, msg) } },
                                    onQuickAdminLogin = { done -> phoneAuth.quickAdminLogin { ok, msg -> if (ok) { isLoggedIn = true; applyLanguage(DEFAULT_LANG, true) }; done(ok, msg) } },
                                    onLanguageChanged = { l -> applyLanguage(l, false) }
                                )
                                is NavDestination.Chat -> ChatScreen(contact = state.contact, allContacts = allContacts, onBack = { selectedContact = null })
                                is NavDestination.Admin -> AdminScreen(users = firebaseContacts, onDeleteUser = { c, cb -> phoneAuth.deleteUserFromFirestore(c.uid, cb) }, onBack = { showAdmin = false })
                                is NavDestination.Settings -> SettingsScreen(
                                    currentLanguage = currentLanguage, 
                                    onSave = { l -> phoneAuth.updateLanguage(l) { _, _ -> }; applyLanguage(l, true) }, 
                                    onClose = { showSettings = false },
                                    onLogout = {
                                        phoneAuth.signOut()
                                        isLoggedIn = false
                                        showSettings = false
                                        selectedContact = null
                                    }
                                )
                                is NavDestination.Contacts -> ContactsScreen(contacts = allContacts, onBack = { showContacts = false }, onOpenChat = { c -> selectedContact = c })
                                is NavDestination.Home -> HomeScreen(
                                    myUid = auth.currentUser?.uid ?: "",
                                    allContacts = allContacts,
                                    onOpenChat = { selectedContact = it },
                                    onOpenSettings = { showSettings = true },
                                    onOpenContacts = { showContacts = true },
                                    onOpenAdmin = if (currentRole == "admin") {{ showAdmin = true }} else null
                                )
                            }
                        }

                        // Notificación In-App de Mensajes
                        inAppNotification?.let { notificationData ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                                    .align(Alignment.TopCenter)
                                    .clickable { selectedContact = notificationData.first; inAppNotification = null },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                                elevation = CardDefaults.cardElevation(8.dp)
                            ) {
                                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.AutoMirrored.Filled.Message, null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(12.dp))
                                    Column {
                                        Text(notificationData.first.name.ifBlank { notificationData.first.email }, fontWeight = FontWeight.Bold)
                                        Text(notificationData.second, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    Spacer(Modifier.weight(1f))
                                    IconButton(onClick = { inAppNotification = null }) { Icon(Icons.Default.Close, null) }
                                }
                            }
                            LaunchedEffect(notificationData) { kotlinx.coroutines.delay(5000); inAppNotification = null }
                        }

                        // Notificación de Llamada Entrante (Estilo Heads-up de WhatsApp)
                        globalIncomingCall?.let { callData ->
                            val caller = firebaseContacts.find { it.uid == callData.first } ?: Contact(uid = callData.first, name = "Usuario")
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                                    .align(Alignment.TopCenter)
                                    .statusBarsPadding(),
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)),
                                elevation = CardDefaults.cardElevation(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(modifier = Modifier.size(52.dp), shape = CircleShape, color = Color(0xFF673AB7)) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(caller.name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                                        }
                                    }
                                    Spacer(Modifier.width(16.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text("Videollamada entrante", color = Color(0xFF00BFA5), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        Text(caller.name.ifBlank { caller.email }, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    
                                    Row {
                                        IconButton(
                                            onClick = {
                                                ChatManager.endCallSignal(callData.first)
                                                globalIncomingCall = null
                                            },
                                            modifier = Modifier.background(Color(0xFFFF3B30), CircleShape).size(44.dp)
                                        ) { Icon(Icons.Default.CallEnd, null, tint = Color.White) }
                                        
                                        Spacer(Modifier.width(12.dp))
                                        
                                        IconButton(
                                            onClick = {
                                                val intent = Intent(this@MainActivity, VideoCallActivity::class.java).apply { putExtra("CHANNEL_NAME", callData.second) }
                                                startActivity(intent)
                                                globalIncomingCall = null
                                            },
                                            modifier = Modifier.background(Color(0xFF34C759), CircleShape).size(44.dp)
                                        ) { Icon(Icons.Default.Call, null, tint = Color.White) }
                                    }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(
    myUid: String,
    allContacts: List<Contact>,
    onOpenChat: (Contact) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenContacts: () -> Unit,
    onOpenAdmin: (() -> Unit)?
) {
    val db = FirebaseFirestore.getInstance()
    var activeChats by remember { mutableStateOf(listOf<Map<String, Any>>()) }

    LaunchedEffect(myUid) {
        db.collection("chats")
            .whereArrayContains("participants", myUid)
            .addSnapshotListener { snaps, _ ->
                // Ordenamos manualmente por lastTimestamp de forma descendente 
                // para que los chats más recientes aparezcan siempre arriba
                activeChats = snaps?.documents?.map { it.data?.plus("id" to it.id) ?: emptyMap() }
                    ?.sortedByDescending { it["lastTimestamp"] as? Long ?: 0L }
                    ?: emptyList()
            }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("TextMeMail", fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary) },
                actions = {
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Settings, null) }
                    if (onOpenAdmin != null) IconButton(onClick = onOpenAdmin) { Icon(Icons.Default.AdminPanelSettings, null, tint = Color.Red) }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onOpenContacts, containerColor = MaterialTheme.colorScheme.primary, shape = CircleShape) {
                Icon(Icons.Default.Chat, null, tint = Color.White)
            }
        }
    ) { padding ->
        if (activeChats.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.ChatBubbleOutline, null, modifier = Modifier.size(64.dp), tint = Color.LightGray)
                    Spacer(Modifier.height(16.dp))
                    Text("No tienes chats activos. ¡Inicia uno!", color = Color.Gray)
                }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(activeChats) { chat ->
                    val participants = chat["participants"] as? List<*>
                    val otherUid = participants?.firstOrNull { it != myUid } as? String ?: ""
                    val contact = allContacts.find { it.uid == otherUid } ?: Contact(uid = otherUid, name = "Usuario")
                    
                    var unreadCount by remember { mutableIntStateOf(0) }
                    LaunchedEffect(chat["id"]) {
                        db.collection("chats").document(chat["id"] as String).collection("messages")
                            .whereEqualTo("receiverId", myUid)
                            .whereEqualTo("isRead", false)
                            .addSnapshotListener { snaps, _ ->
                                unreadCount = snaps?.size() ?: 0
                            }
                    }

                    ChatListItem(
                        contact = contact,
                        lastMsg = chat["lastMessage"] as? String ?: "",
                        time = chat["lastTimestamp"] as? Long ?: 0L,
                        unreadCount = unreadCount,
                        onClick = { onOpenChat(contact) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = Color.LightGray.copy(alpha = 0.5f))
                }
            }
        }
    }
}

@Composable
fun ChatListItem(contact: Contact, lastMsg: String, time: Long, unreadCount: Int, onClick: () -> Unit) {
    val sdf = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val colorUnread = Color(0xFF00BFA5) 
    val colorBadge = Color(0xFF673AB7) 
    
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(contact.name.ifBlank { contact.email }, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
        supportingContent = { 
            Text(
                lastMsg, 
                maxLines = 1, 
                overflow = TextOverflow.Ellipsis, 
                color = if (unreadCount > 0) Color.Black else Color.Gray,
                fontWeight = if (unreadCount > 0) FontWeight.Medium else FontWeight.Normal
            ) 
        },
        leadingContent = {
            Surface(Modifier.size(52.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(0.1f)) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        contact.name.take(1).ifBlank { contact.email.take(1) }.uppercase(), 
                        fontSize = 20.sp, 
                        fontWeight = FontWeight.Bold, 
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (contact.isOnline) {
                        Surface(
                            modifier = Modifier.size(14.dp).align(Alignment.BottomEnd).offset(x = (-2).dp, y = (-2).dp),
                            shape = CircleShape,
                            color = Color.White
                        ) {
                            Box(modifier = Modifier.padding(2.dp).background(colorUnread, CircleShape))
                        }
                    }
                }
            }
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.Center) {
                Text(
                    if (time > 0) sdf.format(Date(time)) else "", 
                    fontSize = 12.sp, 
                    color = if (unreadCount > 0) colorUnread else Color.Gray,
                    fontWeight = if (unreadCount > 0) FontWeight.Bold else FontWeight.Normal
                )
                if (unreadCount > 0) {
                    Spacer(Modifier.height(6.dp))
                    Surface(color = colorBadge, shape = CircleShape, modifier = Modifier.size(24.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(unreadCount.toString(), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    )
}

@Composable
private fun SettingsScreen(currentLanguage: String, onSave: (String) -> Unit, onLogout: () -> Unit, onClose: () -> Unit) {
    var lang by remember { mutableStateOf(currentLanguage) }
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { 
            IconButton(onClick = onClose) { Icon(Icons.Default.ArrowBack, null) }
            Text(stringResource(R.string.settings), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) 
        }
        Spacer(Modifier.height(32.dp))
        
        Text("Cuenta", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        
        // Botón de Cerrar Sesión (Visible y llamativo)
        Card(
            onClick = onLogout,
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.Logout, null, tint = Color.Red)
                Spacer(Modifier.width(16.dp))
                Text("Cerrar Sesión", color = Color.Red, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            }
        }

        Spacer(Modifier.height(32.dp))
        Text(stringResource(R.string.app_language), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = lang == "es", onClick = { lang = "es" }, label = { Text(stringResource(R.string.spanish)) }, shape = RoundedCornerShape(12.dp))
            FilterChip(selected = lang == "en", onClick = { lang = "en" }, label = { Text(stringResource(R.string.english)) }, shape = RoundedCornerShape(12.dp))
        }

        Spacer(Modifier.weight(1f))
        
        Button(
            onClick = { onSave(lang) }, 
            modifier = Modifier.fillMaxWidth().height(56.dp), 
            shape = RoundedCornerShape(16.dp),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
        ) { 
            Text(stringResource(R.string.save_changes), fontWeight = FontWeight.Bold)
        }
    }
}
