package com.mochistitch.app

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mochistitch.core.settings.ThemeMode
import com.mochistitch.core.ui.StudioTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val vm: StudioViewModel = viewModel()
            val state by vm.state.collectAsState()
            vm.boot(this)
            val dark = when (state.settings.themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            StudioTheme(mode = state.settings.themeMode) {
                val scheme = MaterialTheme.colorScheme
                val view = LocalView.current
                SideEffect {
                    val window = (view.context as Activity).window
                    // Bilah status & navigasi menyatu dengan tema, bukan hitam/putih polos.
                    window.statusBarColor = scheme.primaryContainer.toArgb()
                    window.navigationBarColor = scheme.surface.toArgb()
                    val controller = WindowCompat.getInsetsController(window, view)
                    controller.isAppearanceLightStatusBars = !dark
                    controller.isAppearanceLightNavigationBars = !dark
                }
                Surface(modifier = Modifier.fillMaxSize()) {
                    StudioApp(viewModel = vm, onExitApp = { finish() })
                }
            }
        }
    }
}
