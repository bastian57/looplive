# LoopLive v1.0.0

Video loop player untuk TikTok LIVE via **Bagikan Layar** (screen share).
APK tidak menyentuh TikTok sama sekali — tanpa login, tanpa RTMP, tanpa stream key.

## Cara pakai

1. Pilih video (bisa beberapa = playlist), atur overlay, tap **MODE LIVE**.
2. Buka TikTok > **+** > **LIVE** > **Bagikan layar** > mulai live.
3. Kembali ke LoopLive — video loop fullscreen.

## Engine

- **LoopEngine**: ExoPlayer (media3) playlist repeat-all, auto-retry 5x.
- **OverlayEngine**: jam real-time, ticker promo marquee, kartu harga, QR link.
- **Micro-tricks anti-deteksi**: noise acak tiap 200ms, Ken Burns zoom 1.0>1.04,
  brightness jitter halus.
- **Mode Live**: DND + brightness max + keep-screen-on + immersive fullscreen.
- **MicLock**: kunci mic global (`AudioManager.setMicrophoneMute`), tanpa root.
- **Push-to-Talk**: tahan tombol untuk buka mic sementara.

## Tes audio (Audio Path Detector)

1. Putar video sambil bicara. Kalau suara bicara terdengar di live = mic TikTok ikut.
2. Nyalakan MicLock, bicara lagi. Kalau tinggal suara video yang jernih = SUKSES.
3. Kalau suara video TIDAK terdengar sama sekali = TikTok hanya pakai mic.
   Fallback: Speaker Bridge (video via speaker kencang).

## Peringatan

Loop video rekaman seolah live melanggar aturan TikTok
("do not go LIVE with recordings"). Gunakan dengan risiko sendiri.

## Build

GitHub Actions: push ke `main` (workflow di `.github/workflows/build.yml`)
membangun `LoopLive-v1.0.0.apk` (debug-signed) ke rolling release `latest`.
