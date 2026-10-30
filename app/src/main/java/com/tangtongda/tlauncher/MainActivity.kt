package com.tangtongda.tlauncher

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Density
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.tangtongda.tlauncher.ui.HomeEvents
import com.tangtongda.tlauncher.ui.HomeRoute
import com.tangtongda.tlauncher.ui.theme.TLauncherTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // A launcher must never "go back" out of itself.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = Unit
        })

        // Full screen: hide the status and navigation bars (only exist on phones/tablets; TVs have
        // none, so this changes nothing there). A swipe from the edge shows them briefly.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        setContent {
            TLauncherTheme {
                // The UI is designed for a 1080p TV: 960×540dp. Phones, tablets and TVs with an
                // unusual density would show it bigger or smaller; scale our own layout so the
                // long side is always 960dp. Only affects this app (no `wm density` needed);
                // on a normal TV it works out to the density it already has.
                val config = LocalConfiguration.current
                val base = LocalDensity.current
                val longSidePx = maxOf(config.screenWidthDp, config.screenHeightDp) * base.density
                val tv = if (longSidePx > 0f) Density(longSidePx / 960f, base.fontScale) else base
                CompositionLocalProvider(LocalDensity provides tv) {
                    HomeRoute()
                }
            }
        }
    }

    // HOME pressed while we're already the foreground home screen: jump back to the top of 首页.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) HomeEvents.goHome.tryEmit(Unit)
    }
}
