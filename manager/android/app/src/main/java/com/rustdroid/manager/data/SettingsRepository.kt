package com.rustdroid.manager.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "rustdroid_settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class LanguageMode { SYSTEM, ENGLISH, INDONESIAN }
enum class UpdateChannel { STABLE, BETA, CANARY, CUSTOM }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val languageMode: LanguageMode = LanguageMode.SYSTEM,
    val updateChannel: UpdateChannel = UpdateChannel.STABLE,
    val customChannel: String = "",
    val accentColor: String = "Graphite",
    val selectedBootImagePath: String = "",
    val isPatchApplied: Boolean = false,
    val outputNamingFormat: String = "boot_patched_timestamp.img",
    val verboseLogging: Boolean = false
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val LANGUAGE_MODE = stringPreferencesKey("language_mode")
        val UPDATE_CHANNEL = stringPreferencesKey("update_channel")
        val CUSTOM_CHANNEL = stringPreferencesKey("custom_channel")
        val ACCENT_COLOR = stringPreferencesKey("accent_color")
        val SELECTED_BOOT_IMAGE_PATH = stringPreferencesKey("selected_boot_image_path")
        val IS_PATCH_APPLIED = booleanPreferencesKey("is_patch_applied")
        val OUTPUT_NAMING_FORMAT = stringPreferencesKey("output_naming_format")
        val VERBOSE_LOGGING = booleanPreferencesKey("verbose_logging")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            themeMode = prefs.enumValue(Keys.THEME_MODE, ThemeMode.DARK),
            languageMode = prefs.enumValue(Keys.LANGUAGE_MODE, LanguageMode.SYSTEM),
            updateChannel = prefs.enumValue(Keys.UPDATE_CHANNEL, UpdateChannel.STABLE),
            customChannel = prefs[Keys.CUSTOM_CHANNEL].orEmpty(),
            accentColor = prefs[Keys.ACCENT_COLOR] ?: "Graphite",
            selectedBootImagePath = prefs[Keys.SELECTED_BOOT_IMAGE_PATH].orEmpty(),
            isPatchApplied = prefs[Keys.IS_PATCH_APPLIED] ?: false,
            outputNamingFormat = prefs[Keys.OUTPUT_NAMING_FORMAT] ?: "boot_patched_timestamp.img",
            verboseLogging = prefs[Keys.VERBOSE_LOGGING] ?: false
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) = writeString(Keys.THEME_MODE, mode.name)
    suspend fun setLanguageMode(mode: LanguageMode) = writeString(Keys.LANGUAGE_MODE, mode.name)
    suspend fun setUpdateChannel(channel: UpdateChannel) = writeString(Keys.UPDATE_CHANNEL, channel.name)
    suspend fun setCustomChannel(channel: String) = writeString(Keys.CUSTOM_CHANNEL, channel.trim())
    suspend fun setAccentColor(color: String) = writeString(Keys.ACCENT_COLOR, color)
    suspend fun setSelectedBootImagePath(path: String) = writeString(Keys.SELECTED_BOOT_IMAGE_PATH, path)
    suspend fun setPatchApplied(applied: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.IS_PATCH_APPLIED] = applied
        }
    }
    suspend fun setOutputNamingFormat(format: String) = writeString(Keys.OUTPUT_NAMING_FORMAT, format)
    suspend fun setVerboseLogging(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.VERBOSE_LOGGING] = enabled
        }
    }

    private suspend fun writeString(key: Preferences.Key<String>, value: String) {
        context.dataStore.edit { prefs ->
            prefs[key] = value
        }
    }

    private inline fun <reified T : Enum<T>> Preferences.enumValue(
        key: Preferences.Key<String>,
        default: T
    ): T {
        val raw = this[key] ?: return default
        return enumValues<T>().firstOrNull { it.name == raw } ?: default
    }
}
