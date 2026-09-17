package com.mochistitch.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mochistitch.core.ui.MochiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val vm: MainViewModel = viewModel()
            val uiState by vm.uiState.collectAsState()
            vm.initSettings(this)
            MochiTheme(mode = uiState.settings.themeMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainScreen(viewModel = vm, onExitApp = { finish() })
                }
            }
        }
    }
}
