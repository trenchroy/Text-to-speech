package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import com.example.ArLabsApp
import com.example.MainActivity
import com.example.R

class TtsPlaybackService : Service() {

    companion object {
        const val ACTION_START = "com.arlabs.app.ACTION_START"
        const val ACTION_UPDATE = "com.arlabs.app.ACTION_UPDATE"
        const val ACTION_PLAY = "com.arlabs.app.ACTION_PLAY"
        const val ACTION_PAUSE = "com.arlabs.app.ACTION_PAUSE"
        const val ACTION_STOP = "com.arlabs.app.ACTION_STOP"
        private const val CHANNEL_ID = "ar_labs_playback_channel"
        private const val NOTIFICATION_ID = 1001
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private var mediaSession: MediaSession? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        val engine = (application as? ArLabsApp)?.container?.playbackEngine ?: return@OnAudioFocusChangeListener
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                engine.pause()
                updateMediaNotification()
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                // Keep manual resume or let user continue smoothly
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        mediaSession = MediaSession(this, "ARLabsTtsSession").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() {
                    (application as? ArLabsApp)?.container?.playbackEngine?.resume()
                    updateMediaNotification()
                }

                override fun onPause() {
                    (application as? ArLabsApp)?.container?.playbackEngine?.pause()
                    updateMediaNotification()
                }

                override fun onStop() {
                    (application as? ArLabsApp)?.container?.playbackEngine?.stop()
                    stopSelfSafely()
                }
            })
            isActive = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val engine = (application as? ArLabsApp)?.container?.playbackEngine
        when (intent?.action) {
            ACTION_START -> {
                acquireWakeLock()
                requestAudioFocus()
                promoteToForeground()
            }
            ACTION_UPDATE -> {
                updateMediaNotification()
            }
            ACTION_PLAY -> {
                requestAudioFocus()
                engine?.resume()
                updateMediaNotification()
            }
            ACTION_PAUSE -> {
                engine?.pause()
                updateMediaNotification()
            }
            ACTION_STOP -> {
                engine?.stop()
                stopSelfSafely()
            }
            else -> {
                promoteToForeground()
            }
        }
        return START_NOT_STICKY
    }

    private fun promoteToForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateMediaNotification() {
        val state = (application as? ArLabsApp)?.container?.playbackEngine?.state?.value ?: return
        if (!state.hasActiveAudio) {
            stopSelfSafely()
            return
        }
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val state = (application as? ArLabsApp)?.container?.playbackEngine?.state?.value
        val isPlaying = state?.isPlaying == true
        val partDisplay = if ((state?.totalParts ?: 0) > 0) {
            "Part ${(state?.currentPartIndex ?: 0) + 1} of ${state?.totalParts}"
        } else {
            "Taiyaar ho raha hai..."
        }
        val voiceLabel = state?.activeVoiceName?.ifBlank { "AR Labs Voice" } ?: "AR Labs Voice"
        val contentText = if (state?.isBuffering == true) {
            "$partDisplay • Buffering..."
        } else if (isPlaying) {
            "$partDisplay • Chal raha hai"
        } else {
            "$partDisplay • Ruka hua hai"
        }

        val sessionState = PlaybackState.Builder()
            .setActions(
                PlaybackState.ACTION_PLAY or
                    PlaybackState.ACTION_PAUSE or
                    PlaybackState.ACTION_STOP
            )
            .setState(
                if (isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                state?.elapsedMs ?: 0L,
                state?.speed ?: 1.0f
            )
            .build()
        mediaSession?.setPlaybackState(sessionState)

        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPending = PendingIntent.getService(
            this,
            1,
            Intent(this, TtsPlaybackService::class.java).setAction(ACTION_PLAY),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val pausePending = PendingIntent.getService(
            this,
            2,
            Intent(this, TtsPlaybackService::class.java).setAction(ACTION_PAUSE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopPending = PendingIntent.getService(
            this,
            3,
            Intent(this, TtsPlaybackService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playAction = Notification.Action.Builder(
            android.R.drawable.ic_media_play,
            "Play",
            playPending
        ).build()

        val pauseAction = Notification.Action.Builder(
            android.R.drawable.ic_media_pause,
            "Pause",
            pausePending
        ).build()

        val stopAction = Notification.Action.Builder(
            android.R.drawable.ic_menu_close_clear_cancel,
            "Stop",
            stopPending
        ).build()

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        return builder
            .setContentTitle("AR Labs • $voiceLabel")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(openAppIntent)
            .setOngoing(isPlaying)
            .setOnlyAlertOnce(true)
            .addAction(playAction)
            .addAction(pauseAction)
            .addAction(stopAction)
            .setStyle(
                Notification.MediaStyle()
                    .setMediaSession(mediaSession?.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notif_channel_desc)
                setShowBadge(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ARLabs:TtsPlaybackLock").apply {
            setReferenceCounted(false)
            acquire(4 * 60 * 60 * 1000L) // Up to 4 hours continuous non-stop session
        }
    }

    private fun requestAudioFocus() {
        val am = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val req = audioFocusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setOnAudioFocusChangeListener(focusChangeListener)
                .build().also { audioFocusRequest = it }
            am.requestAudioFocus(req)
        } else {
            @Suppress("DEPRECATION")
            am.requestAudioFocus(focusChangeListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        }
    }

    private fun abandonAudioFocus() {
        val am = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            am.abandonAudioFocus(focusChangeListener)
        }
    }

    private fun stopSelfSafely() {
        abandonAudioFocus()
        runCatching {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        abandonAudioFocus()
        runCatching {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        }
        mediaSession?.isActive = false
        mediaSession?.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
