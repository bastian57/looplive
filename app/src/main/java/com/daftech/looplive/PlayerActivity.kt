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
import android.widget.FrameLayout
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
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.Random

class PlayerActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private lateinit var audioManager: AudioManager
    private lateinit var notifManager: NotificationManager
    private var prevFilter = NotificationManager.INTERRUPTION_FILTER_ALL
    private var micLocked = true
    private var dndOn = false
    private val handler = Handler(Looper.getMainLooper())
    private val rnd = Random()
    private var retryCount = 0
    private val sessionStart = System.currentTimeMillis()

    // Anti-deteksi Lapis 2+3
    private var stealthLoop: StealthLoop? = null
    private var handheld: HandheldMotion? = null
    private var audioAlive: AudioAlive? = null
    private var engagement: EngagementSim? = null
    private var engagementOn = false
    private var liveDataOn = false
    private var randomLoopOn = false
    private var countdownLeft = 15 * 60
    private var stock = 20

    // Detik-ticker: jam + durasi live + countdown flash sale
    private val tickRunnable = object : Runnable {
        override fun run() {
            findViewById<TextView>(R.id.tvClock).text =
                SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            val elapsed = (System.currentTimeMillis() - sessionStart) / 1000
            val dur = fmtDur(elapsed)
            if (engagementOn) {
                engagement?.setDuration(dur)
            } else if (liveDataOn) {
                findViewById<TextView>(R.id.tvLiveMeta).text = "🔴 LIVE • $dur"
            }
            if (liveDataOn) {
                countdownLeft--
                if (countdownLeft < 0) countdownLeft = 15 * 60
                findViewById<TextView>(R.id.tvCountdown).text =
                    "⚡ FLASH SALE berakhir ${fmtMs(countdownLeft)}"
            }
            handler.postDelayed(this, 1000)
        }
    }

    private val stockRunnable = object : Runnable {
        override fun run() {
            if (liveDataOn) {
                stock--
                if (stock < 3) stock = 15 + rnd.nextInt(11)
                findViewById<TextView>(R.id.tvStock).text = "Sisa stok: $stock"
                handler.postDelayed(this, (180 + rnd.nextInt(180)) * 1000L)
            }
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
        randomLoopOn = intent.getBooleanExtra("randomLoop", true)
        engagementOn = intent.getBooleanExtra("engagement", true)
        liveDataOn = intent.getBooleanExtra("liveData", true)
        val shuffleOn = intent.getBooleanExtra("shuffle", true)
        val handheldOn = intent.getBooleanExtra("handheld", true)
        val audioAliveOn = intent.getBooleanExtra("audioAlive", true)

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

        // Player + StealthLoop (Lapis 2.1 + 3.1)
        val playerView = findViewById<PlayerView>(R.id.playerView)
        playerView.useController = false
        val items = uris.map { MediaItem.fromUri(it) }
        player = ExoPlayer.Builder(this).build().also { p ->
            p.setMediaItems(items)
            audioAlive = AudioAlive(p, audioAliveOn)
            stealthLoop = StealthLoop(p, randomLoopOn, shuffleOn) {
                if (randomLoopOn) fadeTransition()
                audioAlive?.onCycle()
            }
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

        // Lapis 3.2: handheld breathing, atau fallback Ken Burns lawas
        handheld = HandheldMotion(playerView, handheldOn)
        if (handheldOn) {
            handheld?.start()
        } else {
            ValueAnimator.ofFloat(1f, 1.04f).apply {
                duration = 24000
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
                addUpdateListener { a ->
                    val v = a.animatedValue as Float
                    playerView.scaleX = v
                    playerView.scaleY = v
                }
                start()
            }
        }

        // Brightness jitter lawas — hanya bila tanpa fade transisi
        // (dimView dipakai untuk fade saat randomLoop aktif)
        if (!randomLoopOn) {
            val dimView = findViewById<View>(R.id.dimView)
            ValueAnimator.ofFloat(0f, 0.03f).apply {
                duration = 9000
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
                addUpdateListener { a -> dimView.alpha = a.animatedValue as Float }
                start()
            }
        }

        // Lapis 2.2: simulasi engagement
        val liveBar = findViewById<LinearLayout>(R.id.liveBar)
        liveBar.visibility =
            if (engagementOn || liveDataOn) View.VISIBLE else View.GONE
        engagement = EngagementSim(
            findViewById(R.id.tvLiveMeta),
            findViewById(R.id.tvJoin),
            findViewById(R.id.likeLayer),
            findViewById(R.id.commentLayer),
            engagementOn
        )
        engagement?.start()

        // Lapis 2.3: overlay data hidup
        findViewById<TextView>(R.id.tvCountdown).visibility =
            if (liveDataOn) View.VISIBLE else View.GONE
        findViewById<TextView>(R.id.tvStock).visibility =
            if (liveDataOn) View.VISIBLE else View.GONE
        val tvGreet = findViewById<TextView>(R.id.tvGreet)
        if (liveDataOn) {
            tvGreet.text = greeting()
            tvGreet.visibility = View.VISIBLE
            handler.postDelayed({ tvGreet.visibility = View.GONE }, 6000)
            stock = 15 + rnd.nextInt(11)
            findViewById<TextView>(R.id.tvStock).text = "Sisa stok: $stock"
            handler.postDelayed(stockRunnable, (180 + rnd.nextInt(120)) * 1000L)
        }
        handler.post(tickRunnable)

        // OverlayEngine: jam real-time
        val tvClock = findViewById<TextView>(R.id.tvClock)
        tvClock.visibility = if (showClock) View.VISIBLE else View.GONE

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
        // Mode live bersih: panel kontrol otomatis sembunyi 3 detik setelah mulai
        handler.postDelayed({ controls.visibility = View.GONE }, 3000)

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

    /** Transisi fade hitam singkat tiap siklus — menutupi titik potong loop. */
    private fun fadeTransition() {
        val dim = findViewById<View>(R.id.dimView)
        dim.animate().cancel()
        dim.alpha = 0f
        dim.animate().alpha(0.85f).setDuration(180).withEndAction {
            dim.animate().alpha(0f).setDuration(220).start()
        }.start()
    }

    private fun fmtDur(s: Long): String {
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return "%02d:%02d:%02d".format(h, m, sec)
    }

    private fun fmtMs(s: Int): String {
        return "%02d:%02d".format(s / 60, s % 60)
    }

    private fun greeting(): String {
        return when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 4..10 -> "Selamat pagi kak! \uD83C\uDF05"
            in 11..14 -> "Selamat siang kak! ☀️"
            in 15..17 -> "Selamat sore kak! \uD83C\uDF07"
            else -> "Selamat malam kak! \uD83C\uDF19"
        }
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
        handler.removeCallbacks(tickRunnable)
        handler.removeCallbacks(stockRunnable)
        engagement?.stop()
        handheld?.stop()
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
