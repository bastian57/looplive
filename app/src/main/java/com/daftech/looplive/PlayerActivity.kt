package com.daftech.looplive

import android.animation.ValueAnimator
import android.app.NotificationManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PlayerActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private lateinit var audioManager: AudioManager
    private lateinit var notifManager: NotificationManager
    private var prevFilter = NotificationManager.INTERRUPTION_FILTER_ALL
    private var micLocked = true
    private var dndOn = false
    private val handler = Handler(Looper.getMainLooper())
    private var retryCount = 0

    private val clockRunnable = object : Runnable {
        override fun run() {
            findViewById<TextView>(R.id.tvClock).text =
                SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        notifManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Fullscreen imersif + layar tetap nyala + brightness maksimal
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.attributes = window.attributes.apply { screenBrightness = 1.0f }

        val uris = intent.getStringArrayListExtra("uris") ?: arrayListOf()
        val ticker = intent.getStringExtra("ticker") ?: ""
        val price = intent.getStringExtra("price") ?: ""
        val qr = intent.getStringExtra("qr") ?: ""
        val showClock = intent.getBooleanExtra("clock", true)
        dndOn = intent.getBooleanExtra("dnd", false)
        micLocked = intent.getBooleanExtra("miclock", true)

        // DND: heningkan semua notifikasi selama live
        if (dndOn && notifManager.isNotificationPolicyAccessGranted) {
            prevFilter = notifManager.currentInterruptionFilter
            notifManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
        }

        // MicLock: kunci mic di level OS
        if (micLocked) {
            try {
                audioManager.isMicrophoneMute = true
            } catch (e: Exception) {
                // abaikan bila perangkat menolak
            }
        }

        // LoopEngine: ExoPlayer playlist repeat-all
        val playerView = findViewById<PlayerView>(R.id.playerView)
        playerView.useController = false
        val items = uris.map { MediaItem.fromUri(it) }
        player = ExoPlayer.Builder(this).build().also { p ->
            p.setMediaItems(items)
            p.repeatMode = Player.REPEAT_MODE_ALL
            p.addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    if (retryCount < 5) {
                        retryCount++
                        handler.postDelayed({
                            try {
                                p.prepare()
                                p.play()
                            } catch (e: Exception) {
                                // abaikan, coba lagi di error berikutnya
                            }
                        }, 2000)
                    }
                }
            })
            playerView.player = p
            p.prepare()
            p.play()
        }

        // Micro-trick: Ken Burns zoom 1.0 -> 1.04 (tak kasat mata, hash frame berubah)
        val zoom = ValueAnimator.ofFloat(1f, 1.04f).apply {
            duration = 24000
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
        }
        zoom.addUpdateListener { a ->
            val v = a.animatedValue as Float
            playerView.scaleX = v
            playerView.scaleY = v
        }
        zoom.start()

        // Micro-trick: brightness jitter halus via overlay
        val dimView = findViewById<View>(R.id.dimView)
        ValueAnimator.ofFloat(0f, 0.03f).apply {
            duration = 9000
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { a -> dimView.alpha = a.animatedValue as Float }
            start()
        }

        // OverlayEngine: jam real-time
        val tvClock = findViewById<TextView>(R.id.tvClock)
        if (showClock) {
            tvClock.visibility = View.VISIBLE
            handler.post(clockRunnable)
        } else {
            tvClock.visibility = View.GONE
        }

        // OverlayEngine: ticker promo marquee
        val tvTicker = findViewById<TextView>(R.id.tvTicker)
        if (ticker.isNotBlank()) {
            tvTicker.text = ticker
            tvTicker.isSelected = true
            tvTicker.visibility = View.VISIBLE
        } else {
            tvTicker.visibility = View.GONE
        }

        // OverlayEngine: kartu harga
        val tvPrice = findViewById<TextView>(R.id.tvPrice)
        if (price.isNotBlank()) {
            tvPrice.text = price
            tvPrice.visibility = View.VISIBLE
        } else {
            tvPrice.visibility = View.GONE
        }

        // OverlayEngine: QR link
        val ivQr = findViewById<ImageView>(R.id.ivQr)
        if (qr.isNotBlank()) {
            val bmp = qrBitmap(qr, 384)
            if (bmp != null) {
                ivQr.setImageBitmap(bmp)
                ivQr.visibility = View.VISIBLE
            }
        }

        // Tap layar: tampil/sembunyikan kontrol
        val controls = findViewById<LinearLayout>(R.id.controls)
        findViewById<View>(R.id.root).setOnClickListener {
            controls.visibility =
                if (controls.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        findViewById<Button>(R.id.btnPlayPause).setOnClickListener {
            player?.let { if (it.isPlaying) it.pause() else it.play() }
        }
        findViewById<Button>(R.id.btnNext).setOnClickListener {
            player?.seekToNextMediaItem()
        }

        val swMic = findViewById<Switch>(R.id.swMicLock)
        swMic.isChecked = micLocked
        swMic.setOnCheckedChangeListener { _, checked ->
            micLocked = checked
            try {
                audioManager.isMicrophoneMute = checked
            } catch (e: Exception) {
                // abaikan
            }
        }

        // Push-to-Talk: tahan untuk buka mic sementara
        val btnPtt = findViewById<Button>(R.id.btnPtt)
        btnPtt.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    try {
                        audioManager.isMicrophoneMute = false
                    } catch (ex: Exception) {
                        // abaikan
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (micLocked) {
                        try {
                            audioManager.isMicrophoneMute = true
                        } catch (ex: Exception) {
                            // abaikan
                        }
                    }
                    true
                }
                else -> false
            }
        }

        findViewById<Button>(R.id.btnClose).setOnClickListener { finish() }
    }

    private fun qrBitmap(text: String, size: Int): Bitmap? {
        return try {
            val bits = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
            for (x in 0 until size) {
                for (y in 0 until size) {
                    bmp.setPixel(x, y, if (bits.get(x, y)) Color.BLACK else Color.WHITE)
                }
            }
            bmp
        } catch (e: Exception) {
            null
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(clockRunnable)
        try {
            audioManager.isMicrophoneMute = false
        } catch (e: Exception) {
            // abaikan
        }
        try {
            if (dndOn && notifManager.isNotificationPolicyAccessGranted) {
                notifManager.setInterruptionFilter(prevFilter)
            }
        } catch (e: Exception) {
            // abaikan
        }
        player?.release()
        player = null
        super.onDestroy()
    }
}
