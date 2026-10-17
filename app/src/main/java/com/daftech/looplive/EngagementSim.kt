package com.daftech.looplive

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Random

/**
 * Lapis 2.2 — Simulasi engagement: viewer count random-walk, like melayang,
 * komentar bergilir, notifikasi "bergabung". Semua overlay View biasa di
 * atas video — yang direkam screen-share ikut menangkapnya.
 */
class EngagementSim(
    private val tvLiveMeta: TextView,
    private val tvJoin: TextView,
    private val likeLayer: FrameLayout,
    private val commentLayer: LinearLayout,
    private val enabled: Boolean
) {
    private val handler = Handler(Looper.getMainLooper())
    private val rnd = Random()
    private var viewers = 800 + rnd.nextInt(900)
    private var durationText = "00:00:00"
    private var running = false

    private val names = listOf(
        "Sari***", "Dewi***", "Rina***", "Putri***", "Ani***",
        "Maya***", "Lina***", "Bunda***", "Kak N***", "Toko***"
    )
    private val comments = listOf(
        "Kak ini ori?",
        "Checkout 2 kak!",
        "Ongkir ke Surabaya berapa kak",
        "Bisa COD kak?",
        "Warna apa aja kak?",
        "Sudah checkout, ditunggu ya kak",
        "Minta testimoni dong",
        "Harga segini cuma pas live ya?",
        "Ready stok kak?",
        "Pengiriman dari mana?",
        "Ada voucher nggak kak?",
        "Kakaknya ramah banget"
    )

    fun setDuration(t: String) {
        durationText = t
        refreshMeta()
    }

    private fun refreshMeta() {
        tvLiveMeta.text = "🔴 LIVE • 👁 $viewers • $durationText"
    }

    private val viewerTick = object : Runnable {
        override fun run() {
            if (!running) return
            viewers += rnd.nextInt(21) - 9
            if (rnd.nextFloat() < 0.06f) viewers += rnd.nextInt(60)
            viewers = viewers.coerceIn(120, 9999)
            refreshMeta()
            handler.postDelayed(this, (2500 + rnd.nextInt(4000)).toLong())
        }
    }

    private val likeTick = object : Runnable {
        override fun run() {
            if (!running) return
            spawnLike()
            handler.postDelayed(this, (1500 + rnd.nextInt(3500)).toLong())
        }
    }

    private fun spawnLike() {
        val w = likeLayer.width
        if (w <= 0) return
        val tv = TextView(likeLayer.context).apply {
            text = if (rnd.nextFloat() < 0.8f) "❤" else "🔥"
            textSize = 22f + rnd.nextFloat() * 18f
            setTextColor(
                when (rnd.nextInt(4)) {
                    0 -> Color.parseColor("#FF2C55")
                    1 -> Color.parseColor("#FF6B9D")
                    2 -> Color.parseColor("#FFA500")
                    else -> Color.parseColor("#FF1744")
                }
            )
        }
        val lp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.START
            leftMargin = rnd.nextInt(w * 3 / 4) + w / 8
            bottomMargin = 120
        }
        likeLayer.addView(tv, lp)
        val rise = 500f + rnd.nextFloat() * 400f
        tv.animate()
            .translationY(-rise)
            .alpha(0f)
            .setDuration((2500 + rnd.nextInt(1500)).toLong())
            .withEndAction { likeLayer.removeView(tv) }
            .start()
    }

    private val commentTick = object : Runnable {
        override fun run() {
            if (!running) return
            pushComment()
            handler.postDelayed(this, (5000 + rnd.nextInt(5000)).toLong())
        }
    }

    private fun pushComment() {
        val tv = TextView(commentLayer.context).apply {
            text = "${names[rnd.nextInt(names.size)]}: ${comments[rnd.nextInt(comments.size)]}"
            textSize = 12f
            setTextColor(Color.WHITE)
            setPadding(12, 8, 12, 8)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#AA000000"))
                cornerRadius = 16f
            }
        }
        val lp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 6 }
        commentLayer.addView(tv, lp)
        tv.alpha = 0f
        tv.animate().alpha(1f).setDuration(400).start()
        while (commentLayer.childCount > 3) commentLayer.removeViewAt(0)
    }

    private val joinTick = object : Runnable {
        override fun run() {
            if (!running) return
            tvJoin.text = "${names[rnd.nextInt(names.size)]} bergabung"
            tvJoin.alpha = 0f
            tvJoin.visibility = View.VISIBLE
            tvJoin.animate().alpha(1f).setDuration(400).withEndAction {
                tvJoin.animate().alpha(0f).setDuration(600)
                    .setStartDelay(2200)
                    .withEndAction { tvJoin.visibility = View.GONE }
                    .start()
            }.start()
            handler.postDelayed(this, (20000 + rnd.nextInt(30000)).toLong())
        }
    }

    fun start() {
        if (!enabled || running) return
        running = true
        refreshMeta()
        handler.post(viewerTick)
        handler.postDelayed(likeTick, 2000)
        handler.postDelayed(commentTick, 4000)
        handler.postDelayed(joinTick, 12000)
    }

    fun stop() {
        running = false
        handler.removeCallbacks(viewerTick)
        handler.removeCallbacks(likeTick)
        handler.removeCallbacks(commentTick)
        handler.removeCallbacks(joinTick)
    }
}
