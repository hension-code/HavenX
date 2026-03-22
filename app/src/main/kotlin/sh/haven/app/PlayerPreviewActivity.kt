package com.hension.havenx

import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import sh.haven.feature.sftp.MediaTypeResolver
import java.io.File
import java.util.Locale

class PlayerPreviewActivity : AppCompatActivity() {
    private var player: ExoPlayer? = null
    private val uiHandler = Handler(Looper.getMainLooper())
    private var trackingSeek = false

    private val progressUpdater = object : Runnable {
        override fun run() {
            updateAudioProgress()
            uiHandler.postDelayed(this, 500L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player_preview)

        val filePath = intent.getStringExtra("FILE_PATH")
        val streamUrl = intent.getStringExtra("STREAM_URL")
        val remotePath = intent.getStringExtra("REMOTE_PATH") ?: filePath
        if (remotePath == null) return finish()
        val mediaType = MediaTypeResolver.resolve(remotePath)

        supportActionBar?.title = remotePath.substringAfterLast('/')
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val playerView = findViewById<PlayerView>(R.id.playerView)
        val audioPanel = findViewById<View>(R.id.audioPanel)
        val titleText = findViewById<TextView>(R.id.audioTitle)
        val audioPlayPause = findViewById<ImageButton>(R.id.audioPlayPause)
        val audioSeekBar = findViewById<SeekBar>(R.id.audioSeekBar)
        val audioPosition = findViewById<TextView>(R.id.audioPosition)
        val audioDuration = findViewById<TextView>(R.id.audioDuration)

        if (mediaType == MediaTypeResolver.MediaType.AUDIO) {
            playerView.useController = false
            playerView.visibility = View.GONE
            audioPanel.visibility = View.VISIBLE
            titleText.text = remotePath.substringAfterLast('/')
        }

        player = ExoPlayer.Builder(this).build().also { exo ->
            playerView.player = exo
            val uri = when {
                !streamUrl.isNullOrBlank() -> Uri.parse(streamUrl)
                !filePath.isNullOrBlank() -> Uri.fromFile(File(filePath))
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
        uiHandler.removeCallbacks(progressUpdater)
        player?.release()
        player = null
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
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
}
