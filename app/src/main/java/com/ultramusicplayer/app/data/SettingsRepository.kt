package com.ultramusicplayer.app.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class SongSort(val label: String) {
    TITLE("Title"), ARTIST("Artist"), ALBUM("Album"), DATE_ADDED("Date added"), DURATION("Duration")
}
enum class ShakeAction(val label: String) { NEXT("Next track"), PREVIOUS("Previous track") }

data class AppSettings(
    val onboardingDone: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val accent: Long = DEFAULT_ACCENT,
    val skinId: String = "charcoal",
    val overlay: Float = 0.45f,
    val eqEnabled: Boolean = false,
    val eqPreset: String = "Flat",
    val eqGains: List<Float> = List(5) { 0f },
    val eqCustom: List<Float> = List(5) { 0f },
    val shakeEnabled: Boolean = false,
    val shakeAction: ShakeAction = ShakeAction.NEXT,
    val shakeSensitivity: Float = 0.5f,
    val songSort: SongSort = SongSort.TITLE,
    val songSortDescending: Boolean = false,
    val folders: List<String> = emptyList(),
    val includeAllAudio: Boolean = false,
) {
    companion object {
        const val DEFAULT_ACCENT = 0xFF5EC8F2
    }
}

class SettingsRepository(private val context: Context) {

    private object K {
        val onboarding = booleanPreferencesKey("onboarding_done")
        val theme = stringPreferencesKey("theme_mode")
        val accent = longPreferencesKey("accent")
        val skin = stringPreferencesKey("skin")
        val overlay = floatPreferencesKey("overlay")
        val eqEnabled = booleanPreferencesKey("eq_enabled")
        val eqPreset = stringPreferencesKey("eq_preset")
        val eqGains = stringPreferencesKey("eq_gains")
        val eqCustom = stringPreferencesKey("eq_custom")
        val shake = booleanPreferencesKey("shake_enabled")
        val shakeAction = stringPreferencesKey("shake_action")
        val shakeSens = floatPreferencesKey("shake_sensitivity")
        val sort = stringPreferencesKey("song_sort")
        val sortDesc = booleanPreferencesKey("song_sort_desc")
        val folders = stringSetPreferencesKey("folders")
        val allAudio = booleanPreferencesKey("include_all_audio")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p -> p.toSettings() }

    suspend fun current(): AppSettings = settings.first()

    private fun Preferences.toSettings() = AppSettings(
        onboardingDone = this[K.onboarding] ?: false,
        themeMode = enumOr(this[K.theme], ThemeMode.DARK),
        accent = this[K.accent] ?: AppSettings.DEFAULT_ACCENT,
        skinId = this[K.skin] ?: "charcoal",
        overlay = this[K.overlay] ?: 0.45f,
        eqEnabled = this[K.eqEnabled] ?: false,
        eqPreset = this[K.eqPreset] ?: "Flat",
        eqGains = parseGains(this[K.eqGains]),
        eqCustom = parseGains(this[K.eqCustom]),
        shakeEnabled = this[K.shake] ?: false,
        shakeAction = enumOr(this[K.shakeAction], ShakeAction.NEXT),
        shakeSensitivity = this[K.shakeSens] ?: 0.5f,
        songSort = enumOr(this[K.sort], SongSort.TITLE),
        songSortDescending = this[K.sortDesc] ?: false,
        folders = (this[K.folders] ?: emptySet()).sorted(),
        includeAllAudio = this[K.allAudio] ?: false,
    )

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: default

    private fun parseGains(raw: String?): List<Float> {
        val parts = raw?.split(',')?.mapNotNull { it.toFloatOrNull() }
        return if (parts != null && parts.size == 5) parts else List(5) { 0f }
    }

    private fun List<Float>.encode() = joinToString(",")

    suspend fun setOnboardingDone(done: Boolean) = context.dataStore.edit { it[K.onboarding] = done }
    suspend fun setThemeMode(mode: ThemeMode) = context.dataStore.edit { it[K.theme] = mode.name }
    suspend fun setAccent(argb: Long) = context.dataStore.edit { it[K.accent] = argb }
    suspend fun setSkin(id: String) = context.dataStore.edit { it[K.skin] = id }
    suspend fun setOverlay(v: Float) = context.dataStore.edit { it[K.overlay] = v }
    suspend fun setEq(enabled: Boolean, preset: String, gains: List<Float>) = context.dataStore.edit {
        it[K.eqEnabled] = enabled
        it[K.eqPreset] = preset
        it[K.eqGains] = gains.encode()
    }
    suspend fun setEqCustom(gains: List<Float>) = context.dataStore.edit { it[K.eqCustom] = gains.encode() }
    suspend fun setShake(enabled: Boolean, action: ShakeAction, sensitivity: Float) = context.dataStore.edit {
        it[K.shake] = enabled
        it[K.shakeAction] = action.name
        it[K.shakeSens] = sensitivity
    }
    suspend fun setSongSort(sort: SongSort, descending: Boolean) = context.dataStore.edit {
        it[K.sort] = sort.name
        it[K.sortDesc] = descending
    }
    suspend fun addFolder(uri: String) = context.dataStore.edit { it[K.folders] = (it[K.folders] ?: emptySet()) + uri }
    suspend fun removeFolder(uri: String) = context.dataStore.edit { it[K.folders] = (it[K.folders] ?: emptySet()) - uri }
    suspend fun setIncludeAllAudio(v: Boolean) = context.dataStore.edit { it[K.allAudio] = v }
}
