package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.CompressionLevel
import com.example.data.model.ImageFormatOption
import com.example.data.model.SortField
import com.example.data.model.SortOrder
import com.example.data.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "fileflow_prefs")

data class UserPreferences(
    val onboardingCompleted: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val defaultOutputFolder: String = "Processed",
    val defaultImageFormat: ImageFormatOption = ImageFormatOption.JPG,
    val defaultPdfQuality: CompressionLevel = CompressionLevel.MEDIUM,
    val defaultImageQuality: Int = 85,
    val sortField: SortField = SortField.DATE,
    val sortOrder: SortOrder = SortOrder.DESC,
    val isGridView: Boolean = false
)

class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DEFAULT_OUTPUT_FOLDER = stringPreferencesKey("default_output_folder")
        val DEFAULT_IMAGE_FORMAT = stringPreferencesKey("default_image_format")
        val DEFAULT_PDF_QUALITY = stringPreferencesKey("default_pdf_quality")
        val DEFAULT_IMAGE_QUALITY = intPreferencesKey("default_image_quality")
        val SORT_FIELD = stringPreferencesKey("sort_field")
        val SORT_ORDER = stringPreferencesKey("sort_order")
        val IS_GRID_VIEW = booleanPreferencesKey("is_grid_view")
    }

    val preferencesFlow: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        UserPreferences(
            onboardingCompleted = prefs[Keys.ONBOARDING_COMPLETED] ?: false,
            themeMode = prefs[Keys.THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            defaultOutputFolder = prefs[Keys.DEFAULT_OUTPUT_FOLDER] ?: "Processed",
            defaultImageFormat = prefs[Keys.DEFAULT_IMAGE_FORMAT]?.let { runCatching { ImageFormatOption.valueOf(it) }.getOrNull() } ?: ImageFormatOption.JPG,
            defaultPdfQuality = prefs[Keys.DEFAULT_PDF_QUALITY]?.let { runCatching { CompressionLevel.valueOf(it) }.getOrNull() } ?: CompressionLevel.MEDIUM,
            defaultImageQuality = prefs[Keys.DEFAULT_IMAGE_QUALITY] ?: 85,
            sortField = prefs[Keys.SORT_FIELD]?.let { runCatching { SortField.valueOf(it) }.getOrNull() } ?: SortField.DATE,
            sortOrder = prefs[Keys.SORT_ORDER]?.let { runCatching { SortOrder.valueOf(it) }.getOrNull() } ?: SortOrder.DESC,
            isGridView = prefs[Keys.IS_GRID_VIEW] ?: false
        )
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { it[Keys.ONBOARDING_COMPLETED] = completed }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setDefaultOutputFolder(folder: String) {
        context.dataStore.edit { it[Keys.DEFAULT_OUTPUT_FOLDER] = folder }
    }

    suspend fun setDefaultImageFormat(format: ImageFormatOption) {
        context.dataStore.edit { it[Keys.DEFAULT_IMAGE_FORMAT] = format.name }
    }

    suspend fun setDefaultPdfQuality(quality: CompressionLevel) {
        context.dataStore.edit { it[Keys.DEFAULT_PDF_QUALITY] = quality.name }
    }

    suspend fun setDefaultImageQuality(quality: Int) {
        context.dataStore.edit { it[Keys.DEFAULT_IMAGE_QUALITY] = quality.coerceIn(10, 100) }
    }

    suspend fun setSortPreferences(field: SortField, order: SortOrder) {
        context.dataStore.edit {
            it[Keys.SORT_FIELD] = field.name
            it[Keys.SORT_ORDER] = order.name
        }
    }

    suspend fun setGridView(isGrid: Boolean) {
        context.dataStore.edit { it[Keys.IS_GRID_VIEW] = isGrid }
    }
}
