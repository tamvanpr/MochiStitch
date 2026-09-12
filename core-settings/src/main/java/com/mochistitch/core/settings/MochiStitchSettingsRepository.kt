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
        val KEY_READING_DIRECTION = stringPreferencesKey("reading_direction")
        val KEY_ALIGNMENT_MODE = stringPreferencesKey("alignment_mode")
        val KEY_PADDING_COLOR = stringPreferencesKey("padding_color")
        val KEY_MOCHI_SMART_ENABLED = booleanPreferencesKey("mochi_smart_enabled")
        val KEY_MOCHI_SMART_TOLERANCE = intPreferencesKey("mochi_smart_tolerance")
        val KEY_MOCHI_SMART_SENSITIVITY = stringPreferencesKey("mochi_smart_sensitivity")
        val KEY_SHOW_MANUAL_REVIEW_MARKERS = booleanPreferencesKey("show_manual_review_markers")
        val KEY_AUTO_GUTTER_DETECTION_ENABLED = booleanPreferencesKey("auto_gutter_detection_enabled")
        val KEY_PIXEL_COMPARISON_SENSITIVITY = floatPreferencesKey("pixel_comparison_sensitivity")
        val KEY_PIXEL_COMPARISON_MARGINS = intPreferencesKey("pixel_comparison_margins")
        val KEY_PIXEL_COMPARISON_STEP = intPreferencesKey("pixel_comparison_step")
        val KEY_PIXEL_COMPARISON_MAX_DEVIATION_FACTOR = floatPreferencesKey("pixel_comparison_max_deviation_factor")
    }

    val settingsFlow: Flow<MochiStitchSettings> = dataStore.data.map { prefs ->
        MochiStitchSettings(
            outputFormat = prefs[KEY_OUTPUT_FORMAT]?.let { runCatching { OutputFormat.valueOf(it) }.getOrNull() } ?: OutputFormat.JPG,
            jpgQuality = prefs[KEY_JPG_QUALITY] ?: 90,
            webpQuality = prefs[KEY_WEBP_QUALITY] ?: 90,
            webpLossless = prefs[KEY_WEBP_LOSSLESS] ?: false,
            wrapperFormat = prefs[KEY_WRAPPER_FORMAT]?.let { runCatching { OutputWrapperFormat.valueOf(it) }.getOrNull() } ?: OutputWrapperFormat.LOOSE_FILES,
            projectName = prefs[KEY_PROJECT_NAME] ?: "MochiStitch",
            chapterName = prefs[KEY_CHAPTER_NAME] ?: "1",
            filenameTemplate = prefs[KEY_FILENAME_TEMPLATE] ?: "{project}_ch{chapter}_{index}",
            indexPaddingDigits = prefs[KEY_INDEX_PADDING] ?: 3,
            splitMode = prefs[KEY_SPLIT_MODE]?.let { runCatching { SplitMode.valueOf(it) }.getOrNull() } ?: SplitMode.MAX_PIXELS,
            maxPixelLength = prefs[KEY_MAX_PIXEL_LENGTH] ?: 5000,
            maxPagesPerFile = prefs[KEY_MAX_PAGES_PER_FILE] ?: 10,
            readingDirection = prefs[KEY_READING_DIRECTION]?.let { runCatching { ReadingDirection.valueOf(it) }.getOrNull() } ?: ReadingDirection.LTR,
            alignmentMode = prefs[KEY_ALIGNMENT_MODE]?.let { runCatching { AlignmentModeSetting.valueOf(it) }.getOrNull() } ?: AlignmentModeSetting.RESIZE_PROPORTIONAL,
            paddingColor = prefs[KEY_PADDING_COLOR]?.let { runCatching { PaddingColorSetting.valueOf(it) }.getOrNull() } ?: PaddingColorSetting.WHITE,
            mochiSmartEnabled = prefs[KEY_MOCHI_SMART_ENABLED] ?: true,
            mochiSmartTolerance = prefs[KEY_MOCHI_SMART_TOLERANCE] ?: 150,
            mochiSmartSensitivity = prefs[KEY_MOCHI_SMART_SENSITIVITY]?.let { runCatching { DetectionSensitivity.valueOf(it) }.getOrNull() } ?: DetectionSensitivity.MEDIUM,
            showManualReviewMarkers = prefs[KEY_SHOW_MANUAL_REVIEW_MARKERS] ?: true,
            autoGutterDetectionEnabled = prefs[KEY_AUTO_GUTTER_DETECTION_ENABLED] ?: true,
            pixelComparisonSensitivity = prefs[KEY_PIXEL_COMPARISON_SENSITIVITY] ?: 0.5f,
            pixelComparisonMargins = prefs[KEY_PIXEL_COMPARISON_MARGINS] ?: 0,
            pixelComparisonStep = prefs[KEY_PIXEL_COMPARISON_STEP] ?: 5,
            pixelComparisonMaxDeviationFactor = prefs[KEY_PIXEL_COMPARISON_MAX_DEVIATION_FACTOR] ?: 0.2f
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
            prefs[KEY_READING_DIRECTION] = settings.readingDirection.name
            prefs[KEY_ALIGNMENT_MODE] = settings.alignmentMode.name
            prefs[KEY_PADDING_COLOR] = settings.paddingColor.name
            prefs[KEY_MOCHI_SMART_ENABLED] = settings.mochiSmartEnabled
            prefs[KEY_MOCHI_SMART_TOLERANCE] = settings.mochiSmartTolerance
            prefs[KEY_MOCHI_SMART_SENSITIVITY] = settings.mochiSmartSensitivity.name
            prefs[KEY_SHOW_MANUAL_REVIEW_MARKERS] = settings.showManualReviewMarkers
            prefs[KEY_AUTO_GUTTER_DETECTION_ENABLED] = settings.autoGutterDetectionEnabled
            prefs[KEY_PIXEL_COMPARISON_SENSITIVITY] = settings.pixelComparisonSensitivity
            prefs[KEY_PIXEL_COMPARISON_MARGINS] = settings.pixelComparisonMargins
            prefs[KEY_PIXEL_COMPARISON_STEP] = settings.pixelComparisonStep
            prefs[KEY_PIXEL_COMPARISON_MAX_DEVIATION_FACTOR] = settings.pixelComparisonMaxDeviationFactor
        }
    }
}
