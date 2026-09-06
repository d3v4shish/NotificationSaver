package dev.d3v.notificationsaver

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private const val SETTINGS_DATASTORE_NAME = "notification_saver_settings_v2"
private const val LEGACY_SETTINGS_NAME = "notification_saver_settings"

private val Context.notificationSaverSettings: DataStore<Preferences> by preferencesDataStore(
    name = SETTINGS_DATASTORE_NAME,
    produceMigrations = { context ->
        listOf(SharedPreferencesMigration(context, LEGACY_SETTINGS_NAME))
    },
)

enum class ThemeMode {
    System,
    Light,
    Dark,
}

data class AppSettings(
    val onboardingCompleted: Boolean = false,
    val hideNotificationPreviews: Boolean = false,
    val retentionDays: Int = 0,
    val fallbackCategory: String = DEFAULT_FALLBACK_CATEGORY,
    val appCategoryOverrides: Map<String, String> = emptyMap(),
    val themeMode: ThemeMode = ThemeMode.System,
    val excludedPackages: Set<String> = emptySet(),
    val hideRecentsPreview: Boolean = true,
    val blockScreenshots: Boolean = false,
    val appLockEnabled: Boolean = false,
    val appLockTimeoutMinutes: Int = 1,
    val legacyPinnedThreadIds: Set<String> = emptySet(),
)

const val DEFAULT_FALLBACK_CATEGORY = "General"

class SettingsStore(
    context: Context,
    private val scope: CoroutineScope,
    private val logger: AppLogger,
) {
    private val dataStore = context.notificationSaverSettings
    private val mutableState = MutableStateFlow(loadLegacySettings(context))
    private val mutableInitialized = MutableStateFlow(false)
    val state: StateFlow<AppSettings> = mutableState.asStateFlow()
    val initialized: StateFlow<Boolean> = mutableInitialized.asStateFlow()

    init {
        scope.launch {
            dataStore.data
                .catch { error ->
                    if (error is IOException) {
                        logger.error("SettingsStore", "Failed to read settings; using defaults", error)
                        emit(emptyPreferences())
                    } else {
                        throw error
                    }
                }
                .collect { preferences ->
                    mutableState.value = preferences.toAppSettings()
                    mutableInitialized.value = true
                }
        }
    }

    fun current(): AppSettings = mutableState.value

    fun markOnboardingCompleted() = update { copy(onboardingCompleted = true) }

    fun setHideNotificationPreviews(enabled: Boolean) = update { copy(hideNotificationPreviews = enabled) }

    fun setRetentionDays(days: Int) = update { copy(retentionDays = days.coerceAtLeast(0)) }

    fun setFallbackCategory(category: String) {
        update { copy(fallbackCategory = category.trim().ifEmpty { DEFAULT_FALLBACK_CATEGORY }) }
    }

    fun setThemeMode(themeMode: ThemeMode) = update { copy(themeMode = themeMode) }

    fun setAppCategoryOverride(packageName: String, category: String) {
        val cleanPackage = packageName.trimmedOrNull() ?: return
        val cleanCategory = category.trimmedOrNull() ?: return
        update { copy(appCategoryOverrides = appCategoryOverrides + (cleanPackage to cleanCategory)) }
    }

    fun removeAppCategoryOverride(packageName: String) {
        update { copy(appCategoryOverrides = appCategoryOverrides - packageName) }
    }

    fun setPackageExcluded(packageName: String, excluded: Boolean) {
        val cleanPackage = packageName.trimmedOrNull() ?: return
        update {
            copy(
                excludedPackages = if (excluded) excludedPackages + cleanPackage else excludedPackages - cleanPackage,
            )
        }
    }

    fun setHideRecentsPreview(enabled: Boolean) = update { copy(hideRecentsPreview = enabled) }

    fun setBlockScreenshots(enabled: Boolean) = update { copy(blockScreenshots = enabled) }

    fun setAppLockEnabled(enabled: Boolean) = update { copy(appLockEnabled = enabled) }

    fun setAppLockTimeoutMinutes(minutes: Int) {
        val normalized = minutes.takeIf { it in APP_LOCK_TIMEOUT_OPTIONS } ?: 1
        update { copy(appLockTimeoutMinutes = normalized) }
    }

    fun clearLegacyPinnedThreadIds() = update { copy(legacyPinnedThreadIds = emptySet()) }

    fun applyImportedNonSecretSettings(settings: AppSettings) {
        update {
            copy(
                hideNotificationPreviews = settings.hideNotificationPreviews,
                retentionDays = settings.retentionDays,
                fallbackCategory = settings.fallbackCategory,
                appCategoryOverrides = settings.appCategoryOverrides,
                themeMode = settings.themeMode,
                excludedPackages = settings.excludedPackages,
            )
        }
    }

    fun reset(logAction: Boolean = true) {
        val defaults = AppSettings()
        mutableState.value = defaults
        scope.launch {
            dataStore.edit { it.clear() }
        }
        if (logAction) logger.info("SettingsStore", "Settings reset to defaults")
    }

    private fun update(transform: AppSettings.() -> AppSettings) {
        val updated = mutableState.value.transform()
        mutableState.value = updated
        scope.launch {
            runCatching {
                dataStore.edit { preferences -> preferences.write(updated) }
            }.onFailure { error ->
                logger.error("SettingsStore", "Failed to persist settings", error)
            }
        }
    }

    companion object {
        val APP_LOCK_TIMEOUT_OPTIONS = listOf(0, 1, 5, 15)

        private val KEY_ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        private val KEY_HIDE_PREVIEWS = booleanPreferencesKey("hide_previews")
        private val KEY_RETENTION_DAYS = intPreferencesKey("retention_days")
        private val KEY_FALLBACK_CATEGORY = stringPreferencesKey("fallback_category")
        private val KEY_APP_OVERRIDES = stringPreferencesKey("app_category_overrides")
        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        private val KEY_EXCLUDED_PACKAGES = stringPreferencesKey("excluded_packages")
        private val KEY_HIDE_RECENTS = booleanPreferencesKey("hide_recents_preview")
        private val KEY_BLOCK_SCREENSHOTS = booleanPreferencesKey("block_screenshots")
        private val KEY_APP_LOCK = booleanPreferencesKey("app_lock_enabled")
        private val KEY_APP_LOCK_TIMEOUT = intPreferencesKey("app_lock_timeout_minutes")
        private val KEY_PINNED_THREAD_IDS = stringPreferencesKey("pinned_thread_ids")

        private fun loadLegacySettings(context: Context): AppSettings {
            val preferences = context.getSharedPreferences(LEGACY_SETTINGS_NAME, Context.MODE_PRIVATE)
            if (preferences.all.isEmpty()) return AppSettings()
            return AppSettings(
                onboardingCompleted = preferences.getBoolean("onboarding_completed", false),
                hideNotificationPreviews = preferences.getBoolean("hide_previews", false),
                retentionDays = preferences.getInt("retention_days", 0),
                fallbackCategory = preferences.getString("fallback_category", DEFAULT_FALLBACK_CATEGORY)
                    .trimmedOrNull() ?: DEFAULT_FALLBACK_CATEGORY,
                appCategoryOverrides = decodeAppCategoryOverrides(
                    preferences.getString("app_category_overrides", "{}").orEmpty(),
                ),
                themeMode = decodeThemeMode(preferences.getString("theme_mode", ThemeMode.System.name)),
                legacyPinnedThreadIds = decodeStringSet(
                    preferences.getString("pinned_thread_ids", "[]").orEmpty(),
                ),
            )
        }

        private fun Preferences.toAppSettings(): AppSettings {
            return AppSettings(
                onboardingCompleted = this[KEY_ONBOARDING_COMPLETED] ?: false,
                hideNotificationPreviews = this[KEY_HIDE_PREVIEWS] ?: false,
                retentionDays = this[KEY_RETENTION_DAYS] ?: 0,
                fallbackCategory = this[KEY_FALLBACK_CATEGORY].trimmedOrNull() ?: DEFAULT_FALLBACK_CATEGORY,
                appCategoryOverrides = decodeAppCategoryOverrides(this[KEY_APP_OVERRIDES].orEmpty()),
                themeMode = decodeThemeMode(this[KEY_THEME_MODE]),
                excludedPackages = decodeStringSet(this[KEY_EXCLUDED_PACKAGES].orEmpty()),
                hideRecentsPreview = this[KEY_HIDE_RECENTS] ?: true,
                blockScreenshots = this[KEY_BLOCK_SCREENSHOTS] ?: false,
                appLockEnabled = this[KEY_APP_LOCK] ?: false,
                appLockTimeoutMinutes = this[KEY_APP_LOCK_TIMEOUT]?.takeIf { it in APP_LOCK_TIMEOUT_OPTIONS } ?: 1,
                legacyPinnedThreadIds = decodeStringSet(this[KEY_PINNED_THREAD_IDS].orEmpty()),
            )
        }

        private fun androidx.datastore.preferences.core.MutablePreferences.write(settings: AppSettings) {
            this[KEY_ONBOARDING_COMPLETED] = settings.onboardingCompleted
            this[KEY_HIDE_PREVIEWS] = settings.hideNotificationPreviews
            this[KEY_RETENTION_DAYS] = settings.retentionDays
            this[KEY_FALLBACK_CATEGORY] = settings.fallbackCategory
            this[KEY_APP_OVERRIDES] = JSONObject(settings.appCategoryOverrides).toString()
            this[KEY_THEME_MODE] = settings.themeMode.name
            this[KEY_EXCLUDED_PACKAGES] = JSONArray(settings.excludedPackages.sorted()).toString()
            this[KEY_HIDE_RECENTS] = settings.hideRecentsPreview
            this[KEY_BLOCK_SCREENSHOTS] = settings.blockScreenshots
            this[KEY_APP_LOCK] = settings.appLockEnabled
            this[KEY_APP_LOCK_TIMEOUT] = settings.appLockTimeoutMinutes
            this[KEY_PINNED_THREAD_IDS] = JSONArray(settings.legacyPinnedThreadIds.sorted()).toString()
        }
    }
}

internal fun decodeAppCategoryOverrides(
    raw: String,
    onError: (Throwable) -> Unit = {},
): Map<String, String> {
    if (raw.isBlank()) return emptyMap()
    return runCatching {
        val json = JSONObject(raw)
        buildMap {
            json.keys().forEach { key ->
                val value = json.optString(key).trimmedOrNull()
                if (key.isNotBlank() && value != null) put(key, value)
            }
        }
    }.getOrElse {
        onError(it)
        emptyMap()
    }
}

internal fun decodeThemeMode(raw: String?): ThemeMode {
    return ThemeMode.entries.firstOrNull { it.name == raw } ?: ThemeMode.System
}

internal fun decodeStringSet(
    raw: String,
    onError: (Throwable) -> Unit = {},
): Set<String> {
    if (raw.isBlank()) return emptySet()
    return runCatching {
        val json = JSONArray(raw)
        buildSet {
            for (index in 0 until json.length()) {
                json.optString(index).trimmedOrNull()?.let(::add)
            }
        }
    }.getOrElse {
        onError(it)
        emptySet()
    }
}

internal fun decodePinnedThreadIds(
    raw: String,
    onError: (Throwable) -> Unit = {},
): Set<String> = decodeStringSet(raw, onError)
