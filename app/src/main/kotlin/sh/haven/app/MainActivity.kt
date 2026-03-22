package com.hension.havenx

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.core.os.LocaleListCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import com.hension.havenx.navigation.HavenNavHost
import sh.haven.core.data.preferences.UserPreferencesRepository
import sh.haven.core.security.BiometricAuthenticator
import sh.haven.core.ssh.SshConnectionService
import sh.haven.core.ui.KeyEventInterceptor
import sh.haven.core.ui.theme.HavenTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var preferencesRepository: UserPreferencesRepository
    @Inject lateinit var biometricAuthenticator: BiometricAuthenticator

    private fun exitIfDisconnected() {
        if (SshConnectionService.disconnectedAll) {
            Log.d("MainActivity", "Disconnect All detected — exiting")
            SshConnectionService.clearDisconnectedAll()
            finishAndRemoveTask()
        }
    }

    override fun onResume() {
        super.onResume()
        exitIfDisconnected()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        exitIfDisconnected()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        KeyEventInterceptor.handler?.let { interceptor ->
            if (interceptor(event)) return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            preferencesRepository.languageMode.collect { mode ->
                val locales = mode.tag?.let { LocaleListCompat.forLanguageTags(it) }
                    ?: LocaleListCompat.getEmptyLocaleList()
                AppCompatDelegate.setApplicationLocales(locales)
            }
        }

        enableEdgeToEdge()
        setContent {
            val themeMode by preferencesRepository.theme
                .collectAsState(initial = UserPreferencesRepository.ThemeMode.SYSTEM)
            val darkTheme = when (themeMode) {
                UserPreferencesRepository.ThemeMode.LIGHT -> false
                UserPreferencesRepository.ThemeMode.DARK -> true
                UserPreferencesRepository.ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }

            HavenTheme(darkTheme = darkTheme) {
                val biometricEnabled by preferencesRepository.biometricEnabled
                    .collectAsState(initial = false)
                val lockTimeout by preferencesRepository.lockTimeout
                    .collectAsState(initial = sh.haven.core.data.preferences.UserPreferencesRepository.LockTimeout.IMMEDIATE)
                var unlocked by remember { mutableStateOf(false) }
                var backgroundedAt by remember { mutableStateOf(0L) }

                // Re-lock when app goes to background, respecting timeout
                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_STOP) {
                            backgroundedAt = System.currentTimeMillis()
                        }
                        if (event == Lifecycle.Event.ON_START && unlocked && backgroundedAt > 0) {
                            val elapsed = (System.currentTimeMillis() - backgroundedAt) / 1000
                            if (elapsed >= lockTimeout.seconds) {
                                unlocked = false
                            }
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }

                if (biometricEnabled && !unlocked) {
                    BiometricLockScreen(
                        authenticator = biometricAuthenticator,
                        onUnlocked = { unlocked = true },
                    )
                } else {
                    HavenNavHost(preferencesRepository = preferencesRepository)
                }
            }
        }
    }
}
