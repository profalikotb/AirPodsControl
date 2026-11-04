package com.profalikotb.airpods.probe

import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import com.profalikotb.airpods.core.GestureDeduplicator
import com.profalikotb.airpods.core.MediaCommand
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Time-boxed diagnostic that MEASURES which AirPods gestures reach Android on this phone.
 *
 * To receive media keys, Android requires the receiving app to be the active audio player,
 * so for the probe window only this app holds transient audio focus, plays digital silence
 * and owns an active MediaSession. Everything is released on stop, on timeout, on focus
 * loss (e.g. an incoming call) and when the screen is left. Outside the probe the app never
 * holds a MediaSession, so it cannot steal or duplicate playback controls.
 */
class GestureProbe(private val ctx: Context) {
    data class Observation(val command: MediaCommand, val atMs: Long, val via: String)
    enum class Status { IDLE, RUNNING, BLOCKED_CALL, FAILED }

    private val _status = MutableStateFlow(Status.IDLE); val status: StateFlow<Status> = _status.asStateFlow()
    private val _events = MutableStateFlow<List<Observation>>(emptyList()); val events: StateFlow<List<Observation>> = _events.asStateFlow()

    private val main = Handler(Looper.getMainLooper())
    private val am = ctx.getSystemService(AudioManager::class.java)
    private val dedup = GestureDeduplicator()
    private var session: MediaSession? = null
    private var track: AudioTrack? = null
    private var focus: AudioFocusRequest? = null
    private var volObserver: ContentObserver? = null
    private var lastVol = -1
    private val timeout = Runnable { stop() }

    fun start(durationMs: Long = 60_000) {
        if (_status.value == Status.RUNNING) return
        if (am.mode != AudioManager.MODE_NORMAL) { _status.value = Status.BLOCKED_CALL; return }
        _events.value = emptyList()
        runCatching {
            val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
            focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(attrs)
                .setOnAudioFocusChangeListener({ if (it < 0) stop() }, main).build()
            if (am.requestAudioFocus(focus!!) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) error("focus denied")

            val rate = 22_050; val frames = rate / 2
            track = AudioTrack.Builder().setAudioAttributes(attrs)
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(frames * 2).build().apply {
                    write(ShortArray(frames), 0, frames); setLoopPoints(0, frames, -1); play()
                }

            session = MediaSession(ctx, "AirPodsGestureProbe").apply {
                setCallback(object : MediaSession.Callback() {
                    override fun onMediaButtonEvent(intent: Intent): Boolean {
                        val ke: KeyEvent? = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
                        else @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
                        ke?.let { dedup.onKey(it.keyCode, it.action, it.repeatCount, SystemClock.uptimeMillis())?.let { c -> record(c, KeyEvent.keyCodeToString(ke.keyCode)) } }
                        return true // consumed: nothing is forwarded to any other player
                    }
                    override fun onPlay() = cb(MediaCommand.PLAY_PAUSE, "onPlay")
                    override fun onPause() = cb(MediaCommand.PLAY_PAUSE, "onPause")
                    override fun onSkipToNext() = cb(MediaCommand.NEXT, "onSkipToNext")
                    override fun onSkipToPrevious() = cb(MediaCommand.PREVIOUS, "onSkipToPrevious")
                }, main)
                setPlaybackState(PlaybackState.Builder()
                    .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or
                        PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS)
                    .setState(PlaybackState.STATE_PLAYING, 0, 1f).build())
                isActive = true
            }

            lastVol = am.getStreamVolume(AudioManager.STREAM_MUSIC)
            volObserver = object : ContentObserver(main) {
                override fun onChange(selfChange: Boolean) {
                    val v = am.getStreamVolume(AudioManager.STREAM_MUSIC)
                    if (lastVol >= 0 && v != lastVol) {
                        val c = if (v > lastVol) MediaCommand.VOLUME_UP else MediaCommand.VOLUME_DOWN
                        dedup.onCommand(c, GestureDeduplicator.Path.VOLUME_OBSERVER, SystemClock.uptimeMillis())?.let { record(it, "STREAM_MUSIC $lastVol→$v") }
                    }
                    lastVol = v
                }
            }.also { ctx.contentResolver.registerContentObserver(Settings.System.CONTENT_URI, true, it) }

            _status.value = Status.RUNNING
            main.postDelayed(timeout, durationMs)
        }.onFailure { release(); _status.value = Status.FAILED }
    }

    private fun cb(c: MediaCommand, via: String) {
        dedup.onCommand(c, GestureDeduplicator.Path.TRANSPORT_CALLBACK, SystemClock.uptimeMillis())?.let { record(it, via) }
    }

    private fun record(c: MediaCommand, via: String) { _events.value = _events.value + Observation(c, System.currentTimeMillis(), via) }

    fun stop() {
        release()
        if (_status.value == Status.RUNNING) _status.value = Status.IDLE
    }

    private fun release() {
        main.removeCallbacks(timeout)
        runCatching { session?.isActive = false; session?.release() }; session = null
        runCatching { track?.stop(); track?.release() }; track = null
        runCatching { volObserver?.let { ctx.contentResolver.unregisterContentObserver(it) } }; volObserver = null
        runCatching { focus?.let { am.abandonAudioFocusRequest(it) } }; focus = null
    }
}
