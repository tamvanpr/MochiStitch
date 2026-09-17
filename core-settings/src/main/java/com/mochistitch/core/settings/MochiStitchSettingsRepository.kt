package com.mochistitch.core.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "mochistitch_settings")

class MochiStitchSettingsRepository(private val dataStore: DataStore<Preferences>) {

    constructor(context: Context) : this(context.settingsDataStore)

    companion object {
        val KEY_OUTPUT_FORMAT = stringPreferencesKey("output_format")
        val KEY_JPG_QUALITY = intPreferencesKey("jpg_quality")
        val KEY_WEBP_QUALITY = intPreferencesKey("webp_quality")
        val KEY_WEBP_LOSSLESS = booleanPreferencesKey("webp_lossless")
        val KEY_WRAPPER_FORMAT = stringPreferencesKey("wrapper_format")
        val KEY_PROJECT_NAME = stringPreferencesKey("project_name")
        val KEY_CHAPTER_NAME = stringPreferencesKey("chapter_name")
        val KEY_FILENAME_TEMPLATE = stringPreferencesKey("filename_template")
        val KEY_INDEX_PADDING = intPreferencesKey("index_padding")
        val KEY_SPLIT_MODE = stringPreferencesKey("split_mode")
        val KEY_MAX_PIXEL_LENGTH = intPreferencesKey("max_pixel_length")
        val KEY_MAX_PAGES_PER_FILE = intPreferencesKey("max_pages_per_file")
        val KEY_MOCHI_SMART_ENABLED = booleanPreferencesKey("mochi_smart_enabled")
        val KEY_STRICTNESS = stringPreferencesKey("strictness")
        val KEY_SHOW_MANUAL_REVIEW_MARKERS = booleanPreferencesKey("show_manual_review_markers")
        val KEY_PAPER_TOLERANCE = floatPreferencesKey("paper_tolerance")
        val KEY_ALIGNMENT_MODE = stringPreferencesKey("alignment_mode")
        val KEY_PADDING_COLOR = stringPreferencesKey("padding_color")
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
    }

    val settingsFlow: Flow<MochiStitchSettings> = dataStore.data.map { prefs ->
        MochiStitchSettings(
            outputFormat = prefs[KEY_OUTPUT_FORMAT]?.let { runCatching { OutputFormat.valueOf(it) }.getOrNull() } ?: OutputFormat.JPG,
            jpgQuality = prefs[KEY_JPG_QUALITY] ?: 90,
            webpQuality = prefs[KEY_WEBP_QUALITY] ?: 90,
            webpLossless = prefs[KEY_WEBP_LOSSLESS] ?: false,
            wrapperFormat = prefs[KEY_WRAPPER_FORMAT]?.let { runCatching { OutputWrapperFormat.valueOf(it) }.getOrNull() } ?: OutputWrapperFormat.ZIP,
            projectName = prefs[KEY_PROJECT_NAME] ?: "MochiStitch",
            chapterName = prefs[KEY_CHAPTER_NAME] ?: "1",
            filenameTemplate = prefs[KEY_FILENAME_TEMPLATE] ?: "{project}_ch{chapter}_{index}",
            indexPaddingDigits = prefs[KEY_INDEX_PADDING] ?: 3,
            splitMode = prefs[KEY_SPLIT_MODE]?.let { runCatching { SplitMode.valueOf(it) }.getOrNull() } ?: SplitMode.MAX_PIXELS,
            maxPixelLength = prefs[KEY_MAX_PIXEL_LENGTH] ?: 10000,
            maxPagesPerFile = prefs[KEY_MAX_PAGES_PER_FILE] ?: 10,
            mochiSmartEnabled = prefs[KEY_MOCHI_SMART_ENABLED] ?: true,
            strictness = prefs[KEY_STRICTNESS]?.let { runCatching { Strictness.valueOf(it) }.getOrNull() } ?: Strictness.STRICT,
            showManualReviewMarkers = prefs[KEY_SHOW_MANUAL_REVIEW_MARKERS] ?: true,
            paperTolerance = prefs[KEY_PAPER_TOLERANCE] ?: 12f,
            alignmentMode = prefs[KEY_ALIGNMENT_MODE]?.let { runCatching { AlignmentModeSetting.valueOf(it) }.getOrNull() } ?: AlignmentModeSetting.RESIZE_PROPORTIONAL,
            paddingColor = prefs[KEY_PADDING_COLOR]?.let { runCatching { PaddingColorSetting.valueOf(it) }.getOrNull() } ?: PaddingColorSetting.WHITE,
            themeMode = prefs[KEY_THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM
        )
    }

    suspend fun updateSettings(settings: MochiStitchSettings) {
        dataStore.edit { prefs ->
            prefs[KEY_OUTPUT_FORMAT] = settings.outputFormat.name
            prefs[KEY_JPG_QUALITY] = settings.jpgQuality
            prefs[KEY_WEBP_QUALITY] = settings.webpQuality
            prefs[KEY_WEBP_LOSSLESS] = settings.webpLossless
            prefs[KEY_WRAPPER_FORMAT] = settings.wrapperFormat.name
            prefs[KEY_PROJECT_NAME] = settings.projectName
            prefs[KEY_CHAPTER_NAME] = settings.chapterName
            prefs[KEY_FILENAME_TEMPLATE] = settings.filenameTemplate
            prefs[KEY_INDEX_PADDING] = settings.indexPaddingDigits
            prefs[KEY_SPLIT_MODE] = settings.splitMode.name
            prefs[KEY_MAX_PIXEL_LENGTH] = settings.maxPixelLength
            prefs[KEY_MAX_PAGES_PER_FILE] = settings.maxPagesPerFile
            prefs[KEY_MOCHI_SMART_ENABLED] = settings.mochiSmartEnabled
            prefs[KEY_STRICTNESS] = settings.strictness.name
            prefs[KEY_SHOW_MANUAL_REVIEW_MARKERS] = settings.showManualReviewMarkers
            prefs[KEY_PAPER_TOLERANCE] = settings.paperTolerance
            prefs[KEY_ALIGNMENT_MODE] = settings.alignmentMode.name
            prefs[KEY_PADDING_COLOR] = settings.paddingColor.name
            prefs[KEY_THEME_MODE] = settings.themeMode.name
        }
    }
}
