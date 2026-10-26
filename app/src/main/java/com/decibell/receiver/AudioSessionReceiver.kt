package com.decibell.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import com.decibell.domain.engine.AudioSessionRegistry
import com.decibell.service.AudioEffectService
import com.decibell.util.AppConstants

class AudioSessionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val sessionId = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, AudioEffect.ERROR)
        val packageName = intent.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME).orEmpty()

        if (sessionId < 0) return

        when (action) {
            AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION -> {
                AudioSessionRegistry.onSessionOpened(sessionId, packageName)
                AudioEffectService.notifySessionChanged(
                    context = context,
                    action = AppConstants.ACTION_SESSION_OPENED,
                    sessionId = sessionId,
                    packageName = packageName
                )
            }
            AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION -> {
                AudioSessionRegistry.onSessionClosed(sessionId)
                AudioEffectService.notifySessionChanged(
                    context = context,
                    action = AppConstants.ACTION_SESSION_CLOSED,
                    sessionId = sessionId,
                    packageName = packageName
                )
            }
        }
    }
}
