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

val Context.stitchDataStore: DataStore<Preferences> by preferencesDataStore(name = "mochistitch_v3_settings")

class StitchSettingsRepository(private val dataStore: DataStore<Preferences>) {

    constructor(context: Context) : this(context.stitchDataStore)

    companion object {
        val K_IMAGE_FORMAT = stringPreferencesKey("image_format")
        val K_JPG_Q = intPreferencesKey("jpg_q")
        val K_WEBP_Q = intPreferencesKey("webp_q")
        val K_PACK = stringPreferencesKey("pack")
        val K_SERIES = stringPreferencesKey("series")
        val K_CHAPTER = stringPreferencesKey("chapter")
        val K_PATTERN = stringPreferencesKey("pattern")
        val K_NUMWIDTH = intPreferencesKey("numwidth")
        val K_SPLIT = stringPreferencesKey("split")
        val K_MAXH = intPreferencesKey("maxh")
        val K_PPP = intPreferencesKey("ppp")
        val K_SMART = booleanPreferencesKey("smart")
        val K_STRICT = stringPreferencesKey("strict")
        val K_FLAGS = booleanPreferencesKey("flags")
        val K_PAPER = floatPreferencesKey("paper")
        val K_FIT = stringPreferencesKey("fit")
        val K_MATTE = stringPreferencesKey("matte")
        val K_THEME = stringPreferencesKey("theme")
    }

    val flow: Flow<StitchSettings> = dataStore.data.map { p ->
        StitchSettings(
            imageFormat = p[K_IMAGE_FORMAT]?.let { runCatching { ImageFormat.valueOf(it) }.getOrNull() } ?: ImageFormat.JPG,
            jpgQuality = p[K_JPG_Q] ?: 90,
            webpQuality = p[K_WEBP_Q] ?: 90,
            packFormat = p[K_PACK]?.let { runCatching { PackFormat.valueOf(it) }.getOrNull() } ?: PackFormat.ZIP,
            seriesTitle = p[K_SERIES] ?: "MochiStitch",
            chapterLabel = p[K_CHAPTER] ?: "1",
            namePattern = p[K_PATTERN] ?: "{series}_ch{chapter}_{n}",
            numberWidth = p[K_NUMWIDTH] ?: 3,
            splitRule = p[K_SPLIT]?.let { runCatching { SplitRule.valueOf(it) }.getOrNull() } ?: SplitRule.MAX_HEIGHT,
            maxStripHeight = p[K_MAXH] ?: 10000,
            pagesPerPack = p[K_PPP] ?: 10,
            smartCut = p[K_SMART] ?: true,
            strictness = p[K_STRICT]?.let { runCatching { Strictness.valueOf(it) }.getOrNull() } ?: Strictness.STRICT,
            showReviewFlags = p[K_FLAGS] ?: true,
            paperSensitivity = p[K_PAPER] ?: 12f,
            fitMode = p[K_FIT]?.let { runCatching { FitMode.valueOf(it) }.getOrNull() } ?: FitMode.FIT_WIDTH,
            matteColor = p[K_MATTE]?.let { runCatching { MatteColor.valueOf(it) }.getOrNull() } ?: MatteColor.WHITE,
            themeMode = p[K_THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM
        )
    }

    suspend fun save(s: StitchSettings) {
        dataStore.edit { p ->
            p[K_IMAGE_FORMAT] = s.imageFormat.name
            p[K_JPG_Q] = s.jpgQuality
            p[K_WEBP_Q] = s.webpQuality
            p[K_PACK] = s.packFormat.name
            p[K_SERIES] = s.seriesTitle
            p[K_CHAPTER] = s.chapterLabel
            p[K_PATTERN] = s.namePattern
            p[K_NUMWIDTH] = s.numberWidth
            p[K_SPLIT] = s.splitRule.name
            p[K_MAXH] = s.maxStripHeight
            p[K_PPP] = s.pagesPerPack
            p[K_SMART] = s.smartCut
            p[K_STRICT] = s.strictness.name
            p[K_FLAGS] = s.showReviewFlags
            p[K_PAPER] = s.paperSensitivity
            p[K_FIT] = s.fitMode.name
            p[K_MATTE] = s.matteColor.name
            p[K_THEME] = s.themeMode.name
        }
    }
}
