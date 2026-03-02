package com.example.textmemail.ui_auth

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(
    currentLanguage: String,
    onSaveLanguage: (String, (Boolean, String) -> Unit) -> Unit,
    onBack: () -> Unit
) {
    var lang by remember { mutableStateOf(currentLanguage) }
    var msg by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Ajustes", style = MaterialTheme.typography.headlineSmall)

        Text("Idioma", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            FilterChip(selected = lang == "es", onClick = { lang = "es" }, label = { Text("Español") })
            FilterChip(selected = lang == "en", onClick = { lang = "en" }, label = { Text("English") })
        }

        Button(
            onClick = {
                loading = true
                onSaveLanguage(lang) { ok, text ->
                    loading = false
                    msg = text
                    if (ok) onBack()
                }
            },
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) { Text("Guardar") }

        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth().height(50.dp)) {
            Text("Volver")
        }

        if (loading) CircularProgressIndicator()
        msg?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
    }
}