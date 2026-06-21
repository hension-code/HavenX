package com.hension.havenx

import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import sh.haven.feature.sftp.MediaTypeResolver
import java.io.File
import java.util.Locale
import kotlin.math.abs

class PlayerPreviewActivity : AppCompatActivity() {
    private var player: ExoPlayer? = null
    private var playerView: PlayerView? = null
    private val uiHandler = Handler(Looper.getMainLooper())
    private var trackingSeek = false
    private var fullscreen = false
    private var initialRequestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    private var videoSize: VideoSize? = null

    // Horizontal swipe-to-seek (video only)
    private var seekGestureDetector: GestureDetector? = null
    private var scrubbing = false
    private var scrubTargetMs = 0L

    private val progressUpdater = object : Runnable {
        override fun run() {
            updateAudioProgress()
            uiHandler.postDelayed(this, 500L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initialRequestedOrientation = requestedOrientation
        setContentView(R.layout.activity_player_preview)

        val filePath = intent.getStringExtra("FILE_PATH")
        val streamUrl = intent.getStringExtra("STREAM_URL")
        val remotePath = intent.getStringExtra("REMOTE_PATH") ?: filePath
        if (remotePath == null) return finish()
        // Defence-in-depth: FILE_PATH must be an app cache file, STREAM_URL must
        // be a loopback URL from the local preview server. Reject anything else.
        val safeFilePath = filePath?.takeIf { PreviewIntentGuard.isCachePath(this, it) }
        val safeStreamUrl = streamUrl?.takeIf { PreviewIntentGuard.isLoopbackUrl(it) }
        if (safeFilePath == null && safeStreamUrl == null) return finish()
        val mediaType = MediaTypeResolver.resolve(remotePath)

        supportActionBar?.title = remotePath.substringAfterLast('/')
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val playerView = findViewById<PlayerView>(R.id.playerView).also { this.playerView = it }
        val audioPanel = findViewById<View>(R.id.audioPanel)
        val audioBackgroundGradient = findViewById<View>(R.id.audioBackgroundGradient)
        val titleText = findViewById<TextView>(R.id.audioTitle)
        val audioPlayPause = findViewById<ImageButton>(R.id.audioPlayPause)
        val audioSeekBar = findViewById<SeekBar>(R.id.audioSeekBar)
        val audioPosition = findViewById<TextView>(R.id.audioPosition)
        val audioDuration = findViewById<TextView>(R.id.audioDuration)

        if (mediaType == MediaTypeResolver.MediaType.VIDEO) {
            playerView.setFullscreenButtonClickListener { isFullscreen ->
                setFullscreenMode(isFullscreen)
            }
            setupVideoSeekGestures(playerView)
        }

        if (mediaType == MediaTypeResolver.MediaType.AUDIO) {
            playerView.useController = false
            playerView.visibility = View.GONE
            audioPanel.visibility = View.VISIBLE
            audioBackgroundGradient.visibility = View.VISIBLE
            titleText.text = remotePath.substringAfterLast('/')
        }

        player = ExoPlayer.Builder(this).build().also { exo ->
            playerView.player = exo
            val uri = when {
                !safeStreamUrl.isNullOrBlank() -> Uri.parse(safeStreamUrl)
                !safeFilePath.isNullOrBlank() -> Uri.fromFile(File(safeFilePath))
                else -> null
            } ?: return finish()
            exo.setMediaItem(MediaItem.fromUri(uri))
            exo.prepare()
            exo.playWhenReady = true
            exo.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (mediaType == MediaTypeResolver.MediaType.AUDIO &&
                        playbackState == Player.STATE_ENDED
                    ) {
                        // Reset to start but keep paused (no auto loop).
                        exo.seekTo(0L)
                        exo.playWhenReady = false
                        exo.pause()
                    }
                    updateAudioProgress()
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    syncPlayPauseButton(audioPlayPause, isPlaying)
                }

                override fun onVideoSizeChanged(videoSize: VideoSize) {
                    this@PlayerPreviewActivity.videoSize = videoSize
                    if (fullscreen) {
                        requestedOrientation = fullscreenOrientationFor(videoSize)
                    }
                }
            })
        }

        if (mediaType == MediaTypeResolver.MediaType.AUDIO) {
            audioPlayPause.setOnClickListener {
                val p = player ?: return@setOnClickListener
                if (p.isPlaying) {
                    p.pause()
                } else {
                    if (p.playbackState == Player.STATE_ENDED) {
                        p.seekTo(0L)
                    }
                    p.play()
                }
            }
            audioSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    val p = player ?: return
                    val duration = p.duration.takeIf { it > 0 } ?: return
                    val target = (duration * progress / 1000L).coerceIn(0L, duration)
                    audioPosition.text = formatMillis(target)
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {
                    trackingSeek = true
                }

                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    val p = player
                    if (p == null) {
                        trackingSeek = false
                        return
                    }
                    val duration = p.duration.takeIf { it > 0 }
                    if (duration == null) {
                        trackingSeek = false
                        return
                    }
                    val progress = seekBar?.progress ?: 0
                    val target = (duration * progress / 1000L).coerceIn(0L, duration)
                    p.seekTo(target)
                    trackingSeek = false
                }
            })
            audioPosition.text = "00:00"
            audioDuration.text = "00:00"
            syncPlayPauseButton(audioPlayPause, player?.isPlaying == true)
        }
    }

    override fun onStop() {
        super.onStop()
        uiHandler.removeCallbacks(progressUpdater)
        player?.pause()
    }

    override fun onStart() {
        super.onStart()
        val remotePath = intent.getStringExtra("REMOTE_PATH") ?: return
        if (MediaTypeResolver.resolve(remotePath) == MediaTypeResolver.MediaType.AUDIO) {
            uiHandler.post(progressUpdater)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (fullscreen) {
            setFullscreenMode(false)
        }
        uiHandler.removeCallbacks(progressUpdater)
        player?.release()
        player = null
    }

    override fun onSupportNavigateUp(): Boolean {
        if (fullscreen) {
            exitFullscreen()
            return true
        }
        finish()
        return true
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (fullscreen) {
            exitFullscreen()
        } else {
            super.onBackPressed()
        }
    }

    private fun setupVideoSeekGestures(playerView: PlayerView) {
        // Pixel-to-millisecond factor: ~1px ≈ 120ms scrub speed.
        val pxToMs = 120L

        val detector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float,
            ): Boolean {
                // Only scrub on predominantly horizontal motion; leave vertical
                // gestures (and taps) to PlayerView.
                if (abs(distanceX) <= abs(distanceY)) return false
                val p = player ?: return false
                val duration = p.duration.takeIf { it > 0 } ?: return false

                val base = if (scrubbing) scrubTargetMs else p.currentPosition
                // distanceX is the delta between successive move events; sliding
                // right seeks forward, sliding left seeks back.
                scrubTargetMs = (base - (distanceX * pxToMs).toLong())
                    .coerceIn(0L, duration)
                scrubbing = true
                showSeekIndicator(scrubTargetMs, duration)
                return true
            }
        })
        seekGestureDetector = detector

        // Return false so PlayerView still handles its own click (toggle
        // controller) and controller controls; we only consume the gesture
        // sequence for tracking.
        playerView.setOnTouchListener { _, event ->
            detector.onTouchEvent(event)
            if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
                finishScrub()
            }
            false
        }
    }

    private fun showSeekIndicator(positionMs: Long, durationMs: Long) {
        val indicator = findViewById<TextView?>(R.id.seekIndicator) ?: return
        val sign = if (scrubbing && positionMs > (player?.currentPosition ?: 0L)) "+" else "-"
        indicator.text = "$sign${formatMillis(positionMs)} / ${formatMillis(durationMs)}"
        indicator.visibility = View.VISIBLE
    }

    private fun finishScrub() {
        if (!scrubbing) return
        val p = player
        val indicator = findViewById<TextView?>(R.id.seekIndicator)
        if (p != null) {
            p.seekTo(scrubTargetMs)
            // Reveal the controller briefly so the timeline reflects the jump.
            playerView?.showController()
        }
        scrubbing = false
        indicator?.visibility = View.GONE
    }

    private fun updateAudioProgress() {
        val remotePath = intent.getStringExtra("REMOTE_PATH") ?: return
        if (MediaTypeResolver.resolve(remotePath) != MediaTypeResolver.MediaType.AUDIO) return
        val p = player ?: return
        val seekBar = findViewById<SeekBar>(R.id.audioSeekBar)
        val positionText = findViewById<TextView>(R.id.audioPosition)
        val durationText = findViewById<TextView>(R.id.audioDuration)
        val duration = p.duration.takeIf { it > 0 } ?: 0L
        val position = p.currentPosition.coerceAtLeast(0L)

        if (duration > 0) {
            if (!trackingSeek) {
                val progress = ((position * 1000L) / duration).toInt().coerceIn(0, 1000)
                seekBar.progress = progress
            }
            seekBar.isEnabled = true
            durationText.text = formatMillis(duration)
        } else {
            seekBar.progress = 0
            seekBar.isEnabled = false
            durationText.text = "--:--"
        }
        positionText.text = formatMillis(position)
    }

    private fun formatMillis(ms: Long): String {
        val totalSec = (ms / 1000L).coerceAtLeast(0L)
        val h = totalSec / 3600L
        val m = (totalSec % 3600L) / 60L
        val s = totalSec % 60L
        return if (h > 0) {
            String.format(Locale.getDefault(), "%d:%02d:%02d", h, m, s)
        } else {
            String.format(Locale.getDefault(), "%02d:%02d", m, s)
        }
    }

    private fun syncPlayPauseButton(button: ImageButton, isPlaying: Boolean) {
        button.setImageResource(
            if (isPlaying) android.R.drawable.ic_media_pause
            else android.R.drawable.ic_media_play,
        )
    }

    private fun setFullscreenMode(enabled: Boolean) {
        fullscreen = enabled
        if (enabled) {
            supportActionBar?.hide()
            requestedOrientation = fullscreenOrientationFor(videoSize)
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowInsetsControllerCompat(window, window.decorView).apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            supportActionBar?.show()
            requestedOrientation = initialRequestedOrientation
            WindowCompat.setDecorFitsSystemWindows(window, true)
            WindowInsetsControllerCompat(window, window.decorView)
                .show(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun fullscreenOrientationFor(size: VideoSize?): Int {
        if (size == null || size.width <= 0 || size.height <= 0) {
            return ActivityInfo.SCREEN_ORIENTATION_SENSOR
        }
        val rotated = size.unappliedRotationDegrees % 180 != 0
        val displayWidth = if (rotated) {
            size.height.toFloat()
        } else {
            size.width * size.pixelWidthHeightRatio
        }
        val displayHeight = if (rotated) {
            size.width * size.pixelWidthHeightRatio
        } else {
            size.height.toFloat()
        }
        return when {
            displayHeight > displayWidth -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            displayWidth > displayHeight -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            else -> ActivityInfo.SCREEN_ORIENTATION_SENSOR
        }
    }

    private fun exitFullscreen() {
        val controls = playerView
        val toggled = controls?.findViewById<View>(androidx.media3.ui.R.id.exo_fullscreen)
            ?.performClick() == true ||
            controls?.findViewById<View>(androidx.media3.ui.R.id.exo_minimal_fullscreen)
                ?.performClick() == true
        if (!toggled) {
            setFullscreenMode(false)
        }
    }
}
