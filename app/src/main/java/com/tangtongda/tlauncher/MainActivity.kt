package com.tangtongda.tlauncher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import com.tangtongda.tlauncher.ui.HomeRoute
import com.tangtongda.tlauncher.ui.theme.TLauncherTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // A launcher must never "go back" out of itself.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = Unit
        })

        setContent {
            TLauncherTheme {
                HomeRoute()
            }
        }
    }
}
