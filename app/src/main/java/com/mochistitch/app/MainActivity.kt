package com.mochistitch.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mochistitch.core.settings.ThemeMode
import com.mochistitch.core.ui.StudioTheme

class MainActivity : ComponentActivity() {
    private val sharedVm: StudioViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        handleShared(intent)
        // Gambar di belakang bilah sistem; enableEdgeToEdge membuat bilah
        // transparan agar selalu menyatu dengan tema.
        enableEdgeToEdge()
        setContent {
            val vm: StudioViewModel = viewModel()
            val state by vm.state.collectAsState()
            // Boot DataStore sekali — bukan sebagai efek samping komposisi.
            LaunchedEffect(Unit) { vm.boot(applicationContext) }
            val dark = when (state.settings.themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            StudioTheme(mode = state.settings.themeMode) {
                val view = LocalView.current
                SideEffect {
                    val controller = WindowCompat.getInsetsController(this@MainActivity.window, view)
                    controller.isAppearanceLightStatusBars = !dark
                    controller.isAppearanceLightNavigationBars = !dark
                }
                Surface(modifier = Modifier.fillMaxSize()) {
                    StudioApp(viewModel = vm, onExitApp = { finish() })
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShared(intent)
    }

    /** Teruskan file yang di-share / dibuka ke aplikasi. */
    private fun handleShared(intent: Intent?) {
        if (intent == null) return
        val uris = when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(intent.sharedUri())
            Intent.ACTION_SEND_MULTIPLE -> intent.sharedUris()
            Intent.ACTION_VIEW -> listOfNotNull(intent.data)
            else -> return
        }
        if (uris.isEmpty()) return
        sharedVm.takeShared(uris, applicationContext)
    }

    private fun Intent.sharedUri(): Uri? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
        }

    private fun Intent.sharedUris(): List<Uri> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
        } else {
            @Suppress("DEPRECATION")
            getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
        }
}
