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

    private lateinit var player: ExoPlayer
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

        player = ExoPlayer.Builder(this).build()
        playerView.player = player
        playerView.useController = false

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

        player.addListener(object : Player.Listener {
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
                Toast.makeText(this@PlayerActivity, "播放错误，切换下一个", Toast.LENGTH_SHORT).show()
                playNextVideo()
            }
        })

        playCurrentVideo()

        playerView.setOnClickListener {
            togglePlaybackAndControls()
        }
    }

    private fun setupControls() {
        hideControlsHandler.postDelayed({ hideControls() }, 3000)
    }

    private fun togglePlaybackAndControls() {
        if (player.isPlaying) {
            player.pause()
            showControls()
        } else {
            player.play()
            hideControls()
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
        hideControlsHandler.postDelayed({ if (player.isPlaying) hideControls() }, 3000)
    }

    private fun shuffleAndRandomStart() {
        if (videoUris.size <= 1) return
        videoUris.shuffle(Random.Default)
        currentIndex = Random.nextInt(videoUris.size)
    }

    private fun playCurrentVideo() {
        try {
            val uri = Uri.parse(videoUris[currentIndex])
            filenameText.text = getFileNameFromUri(uri)
            setVideoOrientation(uri)

            player.stop()
            player.setMediaItem(MediaItem.fromUri(uri))
            player.prepare()
            player.play()
        } catch (e: Exception) {
            Toast.makeText(this, "播放失败，切换下一个", Toast.LENGTH_SHORT).show()
            playNextVideo()
        }
    }

    private fun getFileNameFromUri(uri: Uri): String {
        return try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst()) cursor.getString(nameIndex) else "未知视频"
            } ?: "未知视频"
        } catch (e: Exception) {
            "未知视频"
        }
    }

    private fun setVideoOrientation(uri: Uri) {
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(this, uri)
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            requestedOrientation = if (height > width) {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            } else {
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
            retriever.release()
        } catch (e: Exception) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    private fun playNextVideo() {
        currentIndex = (currentIndex + 1) % videoUris.size
        playCurrentVideo()
    }

    private fun playPreviousVideo() {
        currentIndex = if (currentIndex > 0) currentIndex - 1 else videoUris.size - 1
        playCurrentVideo()
    }

    private fun setupGestureDetector() {
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (kotlin.math.abs(velocityX) > 700) {
                    if (velocityX > 0) playPreviousVideo() else playNextVideo()
                    return true
                }
                return false
            }
        })

        playerView.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            false
        }
    }

    private fun setupSeekBar() {
        handler.post(object : Runnable {
            override fun run() {
                if (player.duration > 0 && seekBar.visibility == View.VISIBLE) {
                    seekBar.max = player.duration.toInt()
                    seekBar.progress = player.currentPosition.toInt()
                }
                handler.postDelayed(this, 500)
            }
        })

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) player.seekTo(progress.toLong())
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun rewind5Seconds() {
        val newPosition = (player.currentPosition - 5000).coerceAtLeast(0L)
        player.seekTo(newPosition)
        showControlsTemporarily()
    }

    override fun onPause() {
        super.onPause()
        player.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        hideControlsHandler.removeCallbacksAndMessages(null)
        player.release()
    }
}
