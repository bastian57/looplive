package com.daftech.looplive

import android.animation.ValueAnimator
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import java.util.Random
import kotlin.math.sin

/**
 * Lapis 2.1 — Loop point acak: tiap siklus mulai dari item & posisi acak.
 * Menghancurkan periodisitas yang dibaca detektor loop (autokorelasi).
 * Lapis 3.1 — Shuffle: urutan playlist diacak ulang tiap siklus penuh.
 *
 * Menggantikan REPEAT_MODE_ALL: repeat dimatikan, transisi antar-item
 * dibajak manual. onCycle dipanggil tiap pergantian (untuk fade + audio).
 */
class StealthLoop(
    private val player: Player,
    private val randomLoop: Boolean,
    private val shuffle: Boolean,
    private val onCycle: () -> Unit
) {
    private val rnd = Random()
    private val window = Timeline.Window()
    private var order: MutableList<Int> = mutableListOf()
    private var cursor = 0

    init {
        if (randomLoop) {
            player.repeatMode = Player.REPEAT_MODE_OFF
            reshuffle()
        } else {
            player.repeatMode = Player.REPEAT_MODE_ALL
            if (shuffle) player.setShuffleModeEnabled(true)
        }
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                if (randomLoop && reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                    cycle()
                } else if (!randomLoop) {
                    onCycle()
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (randomLoop && state == Player.STATE_ENDED) cycle()
            }
        })
    }

    private fun reshuffle() {
        val n = player.mediaItemCount
        order = (0 until n).toMutableList()
        if (shuffle && n > 1) order.shuffle(rnd)
        cursor = 0
    }

    private fun randomPos(idx: Int): Long {
        return try {
            val tl = player.currentTimeline
            if (idx < tl.windowCount) {
                tl.getWindow(idx, window)
                val dur = window.durationMs
                if (dur != C.TIME_UNSET && dur > 5000) {
                    (rnd.nextFloat() * (dur - 3000)).toLong()
                } else 0L
            } else 0L
        } catch (e: Exception) {
            0L
        }
    }

    private fun cycle() {
        val n = player.mediaItemCount
        if (n == 0) return
        if (cursor >= order.size) reshuffle()
        val idx = order[cursor++]
        try {
            player.seekTo(idx, randomPos(idx))
            player.play()
        } catch (e: Exception) {
            // abaikan, coba lagi di siklus berikutnya
        }
        onCycle()
    }
}

/**
 * Lapis 3.2 — Handheld breathing: goyangan tangan 6-arah yang halus
 * (translasi + rotasi + zoom drift via sinus multi-frekuensi) plus
 * sesekali "autofocus hunting" (denyut zoom cepat-halus tiap 25-60 dtk)
 * seperti kamera HP beneran. Mengalahkan analisis scene-terlalu-statis.
 */
class HandheldMotion(private val target: View, private val enabled: Boolean) {
    private val handler = Handler(Looper.getMainLooper())
    private val rnd = Random()
    private val ph = FloatArray(6) { rnd.nextFloat() * 6.283f }
    private var t = 0f
    private var running = false
    private var nextHunt = 20f
    private var huntBoost = 0f

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            t += 0.05f
            if (t > nextHunt) {
                nextHunt = t + 25f + rnd.nextFloat() * 35f
                ValueAnimator.ofFloat(0f, 1f, 0f).apply {
                    duration = 900
                    addUpdateListener { a -> huntBoost = (a.animatedValue as Float) * 0.018f }
                    start()
                }
            }
            val dx = (sin(t * 0.9f + ph[0]) + 0.5f * sin(t * 2.3f + ph[1])) * 5f
            val dy = (sin(t * 1.1f + ph[2]) + 0.5f * sin(t * 2.9f + ph[3])) * 5f
            val rot = sin(t * 0.7f + ph[4]) * 0.35f
            val scale = 1.045f + 0.012f * sin(t * 0.23f + ph[5]) + huntBoost
            target.translationX = dx
            target.translationY = dy
            target.rotation = rot
            target.scaleX = scale
            target.scaleY = scale
            handler.postDelayed(this, 50)
        }
    }

    fun start() {
        if (enabled && !running) {
            running = true
            handler.post(tick)
        }
    }

    fun stop() {
        running = false
        handler.removeCallbacks(tick)
    }
}

/**
 * Lapis 3.3 — Audio aliveness: tiap siklus, pitch & tempo digeser mikro
 * acak (±0,5%, tak terdengar telinga) sehingga fingerprint waveform
 * antar-putaran tidak identik.
 */
class AudioAlive(private val player: Player, private val enabled: Boolean) {
    private val rnd = Random()

    fun onCycle() {
        if (!enabled) return
        try {
            val speed = 1f + (rnd.nextFloat() - 0.5f) * 0.01f
            val pitch = 1f + (rnd.nextFloat() - 0.5f) * 0.01f
            player.playbackParameters = PlaybackParameters(speed, pitch)
        } catch (e: Exception) {
            // abaikan
        }
    }
}
