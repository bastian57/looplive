package com.daftech.looplive

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.Settings
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    private val uris = ArrayList<Uri>()
    private lateinit var adapter: PlaylistAdapter
    private lateinit var prefs: SharedPreferences

    private val pickVideos =
        registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { list ->
            list?.forEach { uri ->
                try {
                    contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (e: Exception) {
                    // abaikan: URI tetap bisa dipakai selama sesi ini
                }
                if (!uris.contains(uri)) uris.add(uri)
            }
            saveState()
            adapter.notifyDataSetChanged()
            updateHint()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = getSharedPreferences("looplive", Context.MODE_PRIVATE)
        loadPlaylist()

        val rv = findViewById<RecyclerView>(R.id.rvPlaylist)
        rv.layoutManager = LinearLayoutManager(this)
        adapter = PlaylistAdapter(uris, ::displayName) { pos ->
            if (pos >= 0 && pos < uris.size) {
                uris.removeAt(pos)
                saveState()
                adapter.notifyDataSetChanged()
                updateHint()
            }
        }
        rv.adapter = adapter

        findViewById<EditText>(R.id.etTicker)
            .setText(prefs.getString("ticker", "STOK TERBATAS - CHECKOUT SEKARANG"))
        findViewById<EditText>(R.id.etPrice).setText(prefs.getString("price", ""))
        findViewById<EditText>(R.id.etQr).setText(prefs.getString("qr", ""))
        findViewById<CheckBox>(R.id.cbClock).isChecked = prefs.getBoolean("clock", true)
        findViewById<CheckBox>(R.id.cbDnd).isChecked = prefs.getBoolean("dnd", true)
        findViewById<CheckBox>(R.id.cbMicLock).isChecked = prefs.getBoolean("miclock", true)
        findViewById<CheckBox>(R.id.cbRandomLoop).isChecked = prefs.getBoolean("randomLoop", true)
        findViewById<CheckBox>(R.id.cbEngagement).isChecked = prefs.getBoolean("engagement", true)
        findViewById<CheckBox>(R.id.cbLiveData).isChecked = prefs.getBoolean("liveData", true)
        findViewById<CheckBox>(R.id.cbShuffle).isChecked = prefs.getBoolean("shuffle", true)
        findViewById<CheckBox>(R.id.cbHandheld).isChecked = prefs.getBoolean("handheld", true)
        findViewById<CheckBox>(R.id.cbAudioAlive).isChecked = prefs.getBoolean("audioAlive", true)

        findViewById<Button>(R.id.btnPick).setOnClickListener {
            pickVideos.launch(arrayOf("video/*"))
        }
        findViewById<Button>(R.id.btnLive).setOnClickListener { startLive() }
        findViewById<Button>(R.id.btnTikTok).setOnClickListener { openTikTok() }
        findViewById<Button>(R.id.btnGuide).setOnClickListener { showGuide() }
        updateHint()
    }

    private fun displayName(uri: Uri): String {
        var name = uri.lastPathSegment ?: "video"
        try {
            contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (i >= 0) name = c.getString(i)
                }
            }
        } catch (e: Exception) {
            // abaikan, pakai nama fallback
        }
        return name
    }

    private fun saveState() {
        prefs.edit()
            .putStringSet("playlist", uris.map { it.toString() }.toSet())
            .putString("ticker", findViewById<EditText>(R.id.etTicker).text.toString())
            .putString("price", findViewById<EditText>(R.id.etPrice).text.toString())
            .putString("qr", findViewById<EditText>(R.id.etQr).text.toString())
            .putBoolean("clock", findViewById<CheckBox>(R.id.cbClock).isChecked)
            .putBoolean("dnd", findViewById<CheckBox>(R.id.cbDnd).isChecked)
            .putBoolean("miclock", findViewById<CheckBox>(R.id.cbMicLock).isChecked)
            .putBoolean("randomLoop", findViewById<CheckBox>(R.id.cbRandomLoop).isChecked)
            .putBoolean("engagement", findViewById<CheckBox>(R.id.cbEngagement).isChecked)
            .putBoolean("liveData", findViewById<CheckBox>(R.id.cbLiveData).isChecked)
            .putBoolean("shuffle", findViewById<CheckBox>(R.id.cbShuffle).isChecked)
            .putBoolean("handheld", findViewById<CheckBox>(R.id.cbHandheld).isChecked)
            .putBoolean("audioAlive", findViewById<CheckBox>(R.id.cbAudioAlive).isChecked)
            .apply()
    }

    private fun loadPlaylist() {
        prefs.getStringSet("playlist", emptySet())?.forEach { s ->
            try {
                uris.add(Uri.parse(s))
            } catch (e: Exception) {
                // abaikan entri rusak
            }
        }
    }

    private fun updateHint() {
        findViewById<TextView>(R.id.tvCount).text = "${uris.size} video dalam playlist"
    }

    private fun startLive() {
        if (uris.isEmpty()) {
            Toast.makeText(this, "Pilih minimal 1 video dulu cuk", Toast.LENGTH_SHORT).show()
            return
        }
        saveState()
        val dnd = findViewById<CheckBox>(R.id.cbDnd).isChecked
        val nm = getSystemService(NotificationManager::class.java)
        if (dnd && !nm.isNotificationPolicyAccessGranted) {
            Toast.makeText(this, "Izinkan akses DND dulu di setelan", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            return
        }
        val i = Intent(this, PlayerActivity::class.java).apply {
            putStringArrayListExtra("uris", ArrayList(uris.map { it.toString() }))
            putExtra("ticker", findViewById<EditText>(R.id.etTicker).text.toString())
            putExtra("price", findViewById<EditText>(R.id.etPrice).text.toString())
            putExtra("qr", findViewById<EditText>(R.id.etQr).text.toString())
            putExtra("clock", findViewById<CheckBox>(R.id.cbClock).isChecked)
            putExtra("dnd", dnd)
            putExtra("miclock", findViewById<CheckBox>(R.id.cbMicLock).isChecked)
            putExtra("randomLoop", findViewById<CheckBox>(R.id.cbRandomLoop).isChecked)
            putExtra("engagement", findViewById<CheckBox>(R.id.cbEngagement).isChecked)
            putExtra("liveData", findViewById<CheckBox>(R.id.cbLiveData).isChecked)
            putExtra("shuffle", findViewById<CheckBox>(R.id.cbShuffle).isChecked)
            putExtra("handheld", findViewById<CheckBox>(R.id.cbHandheld).isChecked)
            putExtra("audioAlive", findViewById<CheckBox>(R.id.cbAudioAlive).isChecked)
        }
        startActivity(i)
    }

    private fun openTikTok() {
        val pkgs = listOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill")
        for (p in pkgs) {
            val i = packageManager.getLaunchIntentForPackage(p)
            if (i != null) {
                startActivity(i)
                return
            }
        }
        Toast.makeText(this, "Aplikasi TikTok tidak ditemukan", Toast.LENGTH_SHORT).show()
    }

    private fun showGuide() {
        AlertDialog.Builder(this)
            .setTitle("Panduan & Tes Audio")
            .setMessage(getString(R.string.guide_text))
            .setPositiveButton("Tutup", null)
            .show()
    }
}
