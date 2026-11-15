package com.emotionalleaves.ballot

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.emotionalleaves.ballot.data.AppSettings
import com.emotionalleaves.ballot.ui.BallotRoot
import com.emotionalleaves.ballot.ui.LockScreen
import com.emotionalleaves.ballot.ui.theme.BallotTheme
import com.emotionalleaves.ballot.ui.theme.LocalBallotColors

/** App lock state lives for the whole process, so rotation never unlocks or double-registers. */
object AppLock {
    var locked by mutableStateOf(true)
    @Volatile var autoLockMinutes = 5
    private var backgroundedAt = 0L
    private var installed = false

    fun install() {
        if (installed) return
        installed = true
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) { backgroundedAt = System.currentTimeMillis() }
            override fun onStart(owner: LifecycleOwner) {
                if (backgroundedAt > 0 && System.currentTimeMillis() - backgroundedAt > autoLockMinutes * 60_000L) locked = true
            }
        })
    }
}

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AppLock.install()
        setContent {
            val loaded by produceState<AppSettings?>(null) { graph.settings.flow.collect { value = it } }
            val settings = loaded ?: AppSettings()
            AppLock.autoLockMinutes = settings.autoLockMinutes
            // Status and navigation bar icons follow the app's theme, not the phone's, so they stay visible.
            androidx.compose.runtime.LaunchedEffect(settings.darkTheme) {
                val clear = android.graphics.Color.TRANSPARENT
                val bars = if (settings.darkTheme) androidx.activity.SystemBarStyle.dark(clear) else androidx.activity.SystemBarStyle.light(clear, clear)
                enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
            }
            BallotTheme(dark = settings.darkTheme, textScale = settings.textScale) {
                val c = LocalBallotColors.current
                Box(Modifier.fillMaxSize().background(c.bg).safeDrawingPadding()) {
                    if (loaded == null) {
                        // Settings still loading: show nothing rather than flash unlocked content.
                    } else if (settings.appLock && AppLock.locked && graph.secrets.hasPin()) {
                        LockScreen(onUnlocked = { AppLock.locked = false })
                    } else {
                        BallotRoot()
                    }
                }
            }
        }
    }

}
