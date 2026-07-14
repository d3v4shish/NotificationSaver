package dev.d3v.notificationsaver

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

enum class ThemeMode {
    System,
    Light,
    Dark,
}

data class AppSettings(
    val onboardingCompleted: Boolean = false,
    val hideNotificationPreviews: Boolean = false,
    val retentionDays: Int = 30,
    val fallbackCategory: String = DEFAULT_FALLBACK_CATEGORY,
    val appCategoryOverrides: Map<String, String> = emptyMap(),
    val themeMode: ThemeMode = ThemeMode.System,
    val pinnedThreadIds: Set<String> = emptySet(),
)

const val DEFAULT_FALLBACK_CATEGORY = "General"

class SettingsStore(
    context: Context,
    private val logger: AppLogger,
) {
    private val preferences: SharedPreferences =
        context.getSharedPreferences("notification_saver_settings", Context.MODE_PRIVATE)

    private val mutableState = MutableStateFlow(loadSettings())
    val state: StateFlow<AppSettings> = mutableState.asStateFlow()

    fun current(): AppSettings = mutableState.value

    fun markOnboardingCompleted() {
        update {
            copy(onboardingCompleted = true)
        }
    }

    fun setHideNotificationPreviews(enabled: Boolean) {
        update {
            copy(hideNotificationPreviews = enabled)
        }
    }

    fun setRetentionDays(days: Int) {
        update {
            copy(retentionDays = days)
        }
    }

    fun setFallbackCategory(category: String) {
        val normalized = category.trim().ifEmpty { DEFAULT_FALLBACK_CATEGORY }
        update {
            copy(fallbackCategory = normalized)
        }
    }

    fun setThemeMode(themeMode: ThemeMode) {
        update {
            copy(themeMode = themeMode)
        }
    }

    fun setAppCategoryOverride(packageName: String, category: String) {
        val cleanPackage = packageName.trim()
        val cleanCategory = category.trim()
        if (cleanPackage.isEmpty() || cleanCategory.isEmpty()) {
            return
        }
        update {
            copy(appCategoryOverrides = appCategoryOverrides + (cleanPackage to cleanCategory))
        }
    }

    fun removeAppCategoryOverride(packageName: String) {
        update {
            copy(appCategoryOverrides = appCategoryOverrides - packageName)
        }
    }

    fun pinThread(threadId: String) {
        val cleanId = threadId.trimmedOrNull() ?: return
        update {
            copy(pinnedThreadIds = pinnedThreadIds + cleanId)
        }
    }

    fun unpinThread(threadId: String) {
        val cleanId = threadId.trimmedOrNull() ?: return
        update {
            copy(pinnedThreadIds = pinnedThreadIds - cleanId)
        }
    }

    fun replacePinnedThreadId(oldThreadId: String, newThreadId: String?) {
        val oldId = oldThreadId.trimmedOrNull() ?: return
        val newId = newThreadId.trimmedOrNull()
        update {
            if (oldId !in pinnedThreadIds) {
                return@update this
            }
            val updated = pinnedThreadIds - oldId
            copy(pinnedThreadIds = if (newId == null) updated else updated + newId)
        }
    }

    fun reset(logAction: Boolean = true) {
        preferences.edit().clear().apply()
        mutableState.value = AppSettings()
        if (logAction) {
            logger.info("SettingsStore", "Settings reset to defaults")
        }
    }

    private fun update(transform: AppSettings.() -> AppSettings) {
        val newSettings = mutableState.value.transform()
        saveSettings(newSettings)
        mutableState.value = newSettings
    }

    private fun loadSettings(): AppSettings {
        val overrideJson = preferences.getString(KEY_APP_OVERRIDES, "{}").orEmpty()
        val overrides = decodeAppCategoryOverrides(overrideJson) {
            logger.error("SettingsStore", "Failed to parse category overrides", it)
        }

        return AppSettings(
            onboardingCompleted = preferences.getBoolean(KEY_ONBOARDING_COMPLETED, false),
            hideNotificationPreviews = preferences.getBoolean(KEY_HIDE_PREVIEWS, false),
            retentionDays = preferences.getInt(KEY_RETENTION_DAYS, 30),
            fallbackCategory = preferences.getString(KEY_FALLBACK_CATEGORY, DEFAULT_FALLBACK_CATEGORY)
                ?.takeIf { it.isNotBlank() }
                ?: DEFAULT_FALLBACK_CATEGORY,
            appCategoryOverrides = overrides,
            themeMode = decodeThemeMode(preferences.getString(KEY_THEME_MODE, ThemeMode.System.name)),
            pinnedThreadIds = preferences.getString(KEY_PINNED_THREAD_IDS, "[]")
                ?.let { raw ->
                    decodePinnedThreadIds(raw) { error ->
                        logger.error("SettingsStore", "Failed to parse pinned thread ids", error)
                    }
                }
                ?: emptySet(),
        )
    }

    private fun saveSettings(settings: AppSettings) {
        val overrideJson = JSONObject(settings.appCategoryOverrides).toString()
        preferences.edit()
            .putBoolean(KEY_ONBOARDING_COMPLETED, settings.onboardingCompleted)
            .putBoolean(KEY_HIDE_PREVIEWS, settings.hideNotificationPreviews)
            .putInt(KEY_RETENTION_DAYS, settings.retentionDays)
            .putString(KEY_FALLBACK_CATEGORY, settings.fallbackCategory)
            .putString(KEY_APP_OVERRIDES, overrideJson)
            .putString(KEY_THEME_MODE, settings.themeMode.name)
            .putString(KEY_PINNED_THREAD_IDS, JSONArray(settings.pinnedThreadIds.sorted()).toString())
            .apply()
    }

    companion object {
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        private const val KEY_HIDE_PREVIEWS = "hide_previews"
        private const val KEY_RETENTION_DAYS = "retention_days"
        private const val KEY_FALLBACK_CATEGORY = "fallback_category"
        private const val KEY_APP_OVERRIDES = "app_category_overrides"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_PINNED_THREAD_IDS = "pinned_thread_ids"
    }
}

internal fun decodeAppCategoryOverrides(
    raw: String,
    onError: (Throwable) -> Unit = {},
): Map<String, String> {
    if (raw.isBlank()) {
        return emptyMap()
    }
    return runCatching {
        val jsonObject = JSONObject(raw)
        buildMap {
            jsonObject.keys().forEach { key ->
                put(key, jsonObject.optString(key))
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

internal fun decodePinnedThreadIds(
    raw: String,
    onError: (Throwable) -> Unit = {},
): Set<String> {
    if (raw.isBlank()) {
        return emptySet()
    }
    return runCatching {
        val jsonArray = JSONArray(raw)
        buildSet {
            for (index in 0 until jsonArray.length()) {
                jsonArray.optString(index).trimmedOrNull()?.let(::add)
            }
        }
    }.getOrElse {
        onError(it)
        emptySet()
    }
}
