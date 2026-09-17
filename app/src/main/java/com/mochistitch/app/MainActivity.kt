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
import com.mochistitch.core.ui.StudioTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val vm: StudioViewModel = viewModel()
            val state by vm.state.collectAsState()
            vm.boot(this)
            StudioTheme(mode = state.settings.themeMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    StudioApp(viewModel = vm, onExitApp = { finish() })
                }
            }
        }
    }
}
