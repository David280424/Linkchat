package com.example.textmemail

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("settings")

object LanguagePrefs {
    private val KEY_LANG = stringPreferencesKey("language")

    fun flow(context: Context) = context.dataStore.data.map { it[KEY_LANG] ?: "es" }

    suspend fun set(context: Context, lang: String) {
        context.dataStore.edit { it[KEY_LANG] = lang }
    }
}