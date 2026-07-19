package com.system.helper

import android.content.pm.ActivityInfo
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlin.random.Random

class PlayerActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var seekBar: SeekBar
    private lateinit var filenameText: TextView
    private lateinit var rewindButton: Button

    private lateinit var videoUris: ArrayList<String>
    private var currentIndex = 0

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var gestureDetector: GestureDetector
    private val hideControlsHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        setContentView(R.layout.activity_player)

        playerView = findViewById(R.id.playerView)
        seekBar = findViewById(R.id.seekBar)
        filenameText = findViewById(R.id.filenameText)
        rewindButton = findViewById(R.id.rewindButton)
        rewindButton.setOnClickListener { rewind5Seconds() }

        videoUris = intent.getStringArrayListExtra("video_list") ?: arrayListOf()
        currentIndex = intent.getIntExtra("current_index", 0)

        if (videoUris.isEmpty()) {
            Toast.makeText(this, "没有视频可播放", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        shuffleAndRandomStart()
        setupGestureDetector()
        setupSeekBar()
        setupControls()

        playCurrentVideo()
    }

    private fun initPlayer() {
        player?.release()
        player = ExoPlayer.Builder(this).build().apply {
            playerView.player = this
            addListener(playerListener)
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_ENDED) {
                playNextVideo()
            } else if (state == Player.STATE_READY) {
                showControlsTemporarily()
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) hideControls() else showControls()
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            Toast.makeText(this@PlayerActivity, "播放出错: ${error.message}，切换下一个", Toast.LENGTH_SHORT).show()
            playNextVideo()
        }
    }

    private fun playCurrentVideo() {
        try {
            initPlayer()  // 每次都重建 player，解决状态污染

            val uri = Uri.parse(videoUris[currentIndex])
            filenameText.text = getFileNameFromUri(uri)
            setVideoOrientation(uri)

            player?.let {
                it.setMediaItem(MediaItem.fromUri(uri))
                it.prepare()
                it.play()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "播放失败，切换下一个", Toast.LENGTH_SHORT).show()
            playNextVideo()
        }
    }

    // 其余函数保持不变（setupControls, show/hide, gesture, seek, rewind, orientation 等）
    private fun setupControls() {
        hideControlsHandler.postDelayed({ hideControls() }, 3000)
    }

    private fun togglePlaybackAndControls() {
        player?.let {
            if (it.isPlaying) {
                it.pause()
                showControls()
            } else {
                it.play()
                hideControls()
            }
        }
    }

    private fun showControls() {
        filenameText.visibility = View.VISIBLE
        rewindButton.visibility = View.VISIBLE
        seekBar.visibility = View.VISIBLE
        hideControlsHandler.removeCallbacksAndMessages(null)
    }

    private fun hideControls() {
        filenameText.visibility = View.GONE
        rewindButton.visibility = View.GONE
        seekBar.visibility = View.GONE
    }

    private fun showControlsTemporarily() {
        showControls()
        hideControlsHandler.removeCallbacksAndMessages(null)
        hideControlsHandler.postDelayed({ if (player?.isPlaying == true) hideControls() }, 3000)
    }

    private fun shuffleAndRandomStart() {
        if (videoUris.size <= 1) return
        videoUris.shuffle(Random.Default)
        currentIndex = Random.nextInt(videoUris.size)
    }

    private fun getFileNameFromUri(uri: Uri): String = /* 同之前 */

    private fun setVideoOrientation(uri: Uri) { /* 同之前 */ }

    private fun playNextVideo() {
        currentIndex = (currentIndex + 1) % videoUris.size
        playCurrentVideo()
    }

    private fun playPreviousVideo() {
        currentIndex = if (currentIndex > 0) currentIndex - 1 else videoUris.size - 1
        playCurrentVideo()
    }

    private fun setupGestureDetector() { /* 同之前 */ }

    private fun setupSeekBar() { /* 同之前 */ }

    private fun rewind5Seconds() {
        player?.let {
            if (it.duration > 0) {
                val newPos = (it.currentPosition - 5000).coerceAtLeast(0)
                it.seekTo(newPos)
                showControlsTemporarily()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        player?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        hideControlsHandler.removeCallbacksAndMessages(null)
        player?.release()
        player = null
    }
}
