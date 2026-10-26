package com.decibell.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.audiofx.AudioEffect
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.decibell.MainActivity
import com.decibell.R
import com.decibell.data.local.DecibellDatabase
import com.decibell.data.repository.EqPreferencesRepository
import com.decibell.domain.engine.AudioSessionRegistry
import com.decibell.domain.engine.DynamicsAudioEngine
import com.decibell.domain.engine.EngineStatus
import com.decibell.receiver.AudioSessionReceiver
import com.decibell.util.AppConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class AudioEffectService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var repository: EqPreferencesRepository
    private lateinit var audioEngine: DynamicsAudioEngine
    private val dynamicSessionReceiver = AudioSessionReceiver()
    private var receiverRegistered = false
    private var isForegroundStarted = false

    override fun onCreate() {
        super.onCreate()
        val database = DecibellDatabase.getInstance(applicationContext)
        repository = EqPreferencesRepository(applicationContext, database.eqDao())
        audioEngine = DynamicsAudioEngine.getSharedInstance(applicationContext)
        createNotificationChannel()
        registerDynamicAudioReceiver()

        serviceScope.launch {
            combine(
                repository.stateFlow,
                AudioSessionRegistry.activeSessions
            ) { state, sessions ->
                state to sessions
            }.collect { (state, sessions) ->
                val status = audioEngine.applyState(state, sessions)
                if (state.eqEnabled) {
                    promoteToForeground(state.bandCount, status)
                } else {
                    demoteFromForeground()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            AppConstants.ACTION_STOP_ENGINE -> {
                demoteFromForeground()
                return START_NOT_STICKY
            }
            AppConstants.ACTION_SESSION_OPENED -> {
                val sessionId = intent.getIntExtra(AppConstants.EXTRA_SESSION_ID, -1)
                val pkg = intent.getStringExtra(AppConstants.EXTRA_PACKAGE_NAME).orEmpty()
                if (sessionId > 0) {
                    AudioSessionRegistry.onSessionOpened(sessionId, pkg)
                }
            }
            AppConstants.ACTION_SESSION_CLOSED -> {
                val sessionId = intent.getIntExtra(AppConstants.EXTRA_SESSION_ID, -1)
                if (sessionId > 0) {
                    AudioSessionRegistry.onSessionClosed(sessionId)
                }
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        unregisterDynamicAudioReceiver()
        demoteFromForeground()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun promoteToForeground(bandCount: Int, status: EngineStatus) {
        val notification = buildNotification(bandCount, status)
        try {
            if (!isForegroundStarted) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        AppConstants.NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                    )
                } else {
                    startForeground(AppConstants.NOTIFICATION_ID, notification)
                }
                isForegroundStarted = true
            } else {
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(AppConstants.NOTIFICATION_ID, notification)
            }
        } catch (_: Exception) {
            // In unit tests or restricted background states, audio engine still operates in-process
        }
    }

    private fun demoteFromForeground() {
        if (isForegroundStarted) {
            runCatching {
                stopForeground(STOP_FOREGROUND_REMOVE)
            }
            isForegroundStarted = false
        }
    }

    private fun buildNotification(bandCount: Int, status: EngineStatus): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val targetDesc = when (status) {
            is EngineStatus.ActiveSession -> status.packageName
            is EngineStatus.ActiveGlobalMix -> "Global Mix"
            is EngineStatus.Error -> status.message
            EngineStatus.Inactive -> getString(R.string.value_off)
        }

        return NotificationCompat.Builder(this, AppConstants.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_eq)
            .setContentTitle(getString(R.string.notif_title_active))
            .setContentText(getString(R.string.notif_body_session, bandCount, targetDesc))
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                AppConstants.NOTIFICATION_CHANNEL_ID,
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setShowBadge(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun registerDynamicAudioReceiver() {
        if (receiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
            addAction(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
        }
        ContextCompat.registerReceiver(
            this,
            dynamicSessionReceiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED
        )
        receiverRegistered = true
    }

    private fun unregisterDynamicAudioReceiver() {
        if (!receiverRegistered) return
        runCatching { unregisterReceiver(dynamicSessionReceiver) }
        receiverRegistered = false
    }

    companion object {
        fun syncServiceState(context: Context, eqEnabled: Boolean) {
            val intent = Intent(context, AudioEffectService::class.java).apply {
                action = if (eqEnabled) {
                    AppConstants.ACTION_START_OR_UPDATE_ENGINE
                } else {
                    AppConstants.ACTION_STOP_ENGINE
                }
            }
            runCatching {
                if (eqEnabled) {
                    ContextCompat.startForegroundService(context, intent)
                } else {
                    context.startService(intent)
                }
            }
        }

        fun notifySessionChanged(
            context: Context,
            action: String,
            sessionId: Int,
            packageName: String
        ) {
            val intent = Intent(context, AudioEffectService::class.java).apply {
                this.action = action
                putExtra(AppConstants.EXTRA_SESSION_ID, sessionId)
                putExtra(AppConstants.EXTRA_PACKAGE_NAME, packageName)
            }
            runCatching {
                context.startService(intent)
            }
        }
    }
}
