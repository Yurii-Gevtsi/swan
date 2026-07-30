package com.gysignalstudio.blackswan.engagement

import android.content.Context
import com.gysignalstudio.blackswan.data.local.LocalDataStore
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

object EngagementPromptManager {
    private const val REVIEW_PROMPT_SESSION_GAP = 2

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val sessionRegistered = AtomicBoolean(false)
    private val _showAdFreePrompt = MutableStateFlow(false)
    private val _showRateAppPrompt = MutableStateFlow(false)

    private var interstitialDismissCountThisSession = 0
    private var adFreePromptShownThisSession = false

    val showAdFreePrompt = _showAdFreePrompt.asStateFlow()
    val showRateAppPrompt = _showRateAppPrompt.asStateFlow()

    fun registerSession(context: Context) {
        if (!sessionRegistered.compareAndSet(false, true)) return

        interstitialDismissCountThisSession = 0
        adFreePromptShownThisSession = false
        _showAdFreePrompt.value = false

        val dataStore = LocalDataStore(context.applicationContext)
        scope.launch {
            val sessionCount = dataStore.incrementSessionCount()
            val reviewCompleted = dataStore.rateAppCompleted.first()
            val lastShownSession = dataStore.rateAppLastShownSession.first()
            val shouldShowPrompt = !reviewCompleted &&
                sessionCount >= REVIEW_PROMPT_SESSION_GAP &&
                sessionCount - lastShownSession >= REVIEW_PROMPT_SESSION_GAP

            if (shouldShowPrompt) {
                dataStore.setRateAppLastShownSession(sessionCount)
                _showRateAppPrompt.value = true
            }
        }
    }

    fun recordInterstitialDismiss() {
        interstitialDismissCountThisSession += 1
        if (!adFreePromptShownThisSession && interstitialDismissCountThisSession >= 2) {
            adFreePromptShownThisSession = true
            _showAdFreePrompt.value = true
        }
    }

    fun dismissAdFreePrompt() {
        _showAdFreePrompt.value = false
    }

    fun dismissRateAppPrompt() {
        _showRateAppPrompt.value = false
    }

    fun markRateAppCompleted(context: Context) {
        val dataStore = LocalDataStore(context.applicationContext)
        scope.launch {
            dataStore.setRateAppCompleted(true)
            _showRateAppPrompt.value = false
        }
    }
}
