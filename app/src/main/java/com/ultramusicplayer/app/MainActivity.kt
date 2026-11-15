package com.ultramusicplayer.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.ultramusicplayer.app.ui.AppRoot
import com.ultramusicplayer.app.ui.MainViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AppRoot(viewModel) }
    }

    override fun onResume() {
        super.onResume()
        // Permissions can change in system settings while the app is in the background.
        viewModel.refreshPermission()
    }
}
