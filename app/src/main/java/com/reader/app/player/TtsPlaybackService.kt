package com.reader.app.player

import android.app.Application
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.readium.navigator.media.common.Media3Adapter
import org.readium.navigator.media.common.MediaNavigator
import org.readium.r2.shared.ExperimentalReadiumApi

@OptIn(ExperimentalReadiumApi::class)
typealias AnyMediaNavigator = MediaNavigator<*, *, *>

/**
 * Hosts the [MediaSession] backing the TTS navigator so that reading aloud keeps going while
 * the app is in the background or the screen is off, with playback controls in the system
 * notification and lock screen.
 */
@OptIn(ExperimentalReadiumApi::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class TtsPlaybackService : MediaSessionService() {

    class Session(
        val bookId: Long,
        val navigator: AnyMediaNavigator,
        val mediaSession: MediaSession,
    ) {
        val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    }

    inner class Binder : android.os.Binder() {

        private val sessionMutable: MutableStateFlow<Session?> = MutableStateFlow(null)

        val session: StateFlow<Session?> = sessionMutable.asStateFlow()

        /** Whether we paused playback ourselves because of an audio focus loss (e.g. a phone
         * call), as opposed to the user pausing manually - only then should focus regain
         * auto-resume playback. */
        private var pausedByFocusLoss = false

        private var audioFocusRequest: AudioFocusRequest? = null

        private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
            val navigator = session.value?.navigator ?: return@OnAudioFocusChangeListener
            when (focusChange) {
                AudioManager.AUDIOFOCUS_LOSS,
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK,
                -> {
                    if (navigator.playback.value.playWhenReady) {
                        pausedByFocusLoss = true
                        navigator.pause()
                    }
                }
                AudioManager.AUDIOFOCUS_GAIN -> {
                    if (pausedByFocusLoss) {
                        pausedByFocusLoss = false
                        navigator.play()
                    }
                }
            }
        }

        private fun requestAudioFocus() {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setOnAudioFocusChangeListener(audioFocusListener)
                .build()
            audioFocusRequest = request
            audioManager.requestAudioFocus(request)
        }

        private fun abandonAudioFocus() {
            val request = audioFocusRequest ?: return
            audioFocusRequest = null
            pausedByFocusLoss = false
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.abandonAudioFocusRequest(request)
        }

        fun closeSession() {
            session.value?.let { session ->
                session.mediaSession.release()
                session.coroutineScope.cancel()
                session.navigator.close()
                sessionMutable.value = null
            }
            abandonAudioFocus()
        }

        fun <N> openSession(
            navigator: N,
            bookId: Long,
        ) where N : AnyMediaNavigator, N : Media3Adapter {
            val mediaSession = MediaSession.Builder(applicationContext, navigator.asMedia3Player())
                .setSessionActivity(createSessionActivityIntent())
                .setId(bookId.toString())
                .build()

            addSession(mediaSession)
            sessionMutable.value = Session(bookId, navigator, mediaSession)
            requestAudioFocus()
        }

        private fun createSessionActivityIntent(): PendingIntent {
            var flags = PendingIntent.FLAG_UPDATE_CURRENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                flags = flags or PendingIntent.FLAG_IMMUTABLE
            }
            val intent = application.packageManager.getLaunchIntentForPackage(application.packageName)
            return PendingIntent.getActivity(applicationContext, 0, intent, flags)
        }

        fun stop() {
            closeSession()
            ServiceCompat.stopForeground(this@TtsPlaybackService, ServiceCompat.STOP_FOREGROUND_REMOVE)
            this@TtsPlaybackService.stopSelf()
        }
    }

    private val binder by lazy { Binder() }

    override fun onBind(intent: Intent?): IBinder? {
        return if (intent?.action == SERVICE_INTERFACE) {
            super.onBind(intent)
            binder
        } else {
            super.onBind(intent)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        // Session setup happens afterwards through the bound Binder (see openSession), so there
        // is nothing to validate here yet - just keep the service alive until explicitly stopped.
        return START_NOT_STICKY
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        binder.session.value?.mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        binder.closeSession()
        binder.stop()
    }

    override fun onDestroy() {
        binder.closeSession()
        NotificationManagerCompat.from(this).cancelAll()
        super.onDestroy()
    }

    companion object {
        const val SERVICE_INTERFACE = "com.reader.app.player.TtsPlaybackService"

        fun start(application: Application) {
            application.startService(intent(application))
        }

        fun stop(application: Application) {
            application.stopService(intent(application))
        }

        suspend fun bind(application: Application): Binder {
            val deferred = CompletableDeferred<Binder>()

            val connection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, service: IBinder) {
                    deferred.complete(service as Binder)
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    if (!deferred.isCompleted) {
                        deferred.completeExceptionally(IllegalStateException("TtsPlaybackService disconnected before binding completed."))
                    }
                    // Otherwise: nothing to do, the client observes `Binder.session` for lifecycle changes.
                }

                override fun onNullBinding(name: ComponentName) {
                    if (!deferred.isCompleted) {
                        deferred.completeExceptionally(IllegalStateException("Failed to bind to TtsPlaybackService."))
                    }
                }
            }

            application.bindService(intent(application), connection, 0)
            return deferred.await()
        }

        private fun intent(application: Application) =
            Intent(SERVICE_INTERFACE).apply { setClass(application, TtsPlaybackService::class.java) }
    }
}
