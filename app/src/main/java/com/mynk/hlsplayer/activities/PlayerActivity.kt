package com.mynk.hlsplayer.activities

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import com.mynk.hlsplayer.R
import com.mynk.hlsplayer.player.HlsPlayer
import com.mynk.hlsplayer.player.HlsPlayerView
import com.mynk.hlsplayer.player.WatchHistoryManager
import com.mynk.hlsplayer.player.WatchItem

class PlayerActivity : BaseActivity() {

    private lateinit var playerContainer: FrameLayout
    private lateinit var loader: ProgressBar
    private lateinit var errorLayout: LinearLayout
    private lateinit var btnRetry: android.widget.Button

    private var hlsPlayer: HlsPlayer? = null
    private var hlsPlayerView: HlsPlayerView? = null

    private var streamUrl = ""
    private var movieTitle = ""
    private var contentId = -1
    private var posterUrl = ""
    private var sourceType = 1

    private val handler = Handler(Looper.getMainLooper())
    private val saveProgressRunnable = object : Runnable {
        override fun run() {
            saveProgress()
            handler.postDelayed(this, 10000)
        }
    }

    companion object {
        const val EXTRA_URL = "stream_url"
        const val EXTRA_TITLE = "movie_title"
        const val EXTRA_TYPE = "source_type"
        const val EXTRA_CONTENT_ID = "content_id"
        const val EXTRA_POSTER = "poster_url"
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            window.decorView.systemUiVisibility = (
                android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        setContentView(R.layout.activity_player)

        streamUrl = intent.getStringExtra(EXTRA_URL) ?: ""
        movieTitle = intent.getStringExtra(EXTRA_TITLE) ?: ""
        sourceType = intent.getIntExtra(EXTRA_TYPE, 1)
        contentId = intent.getIntExtra(EXTRA_CONTENT_ID, -1)
        posterUrl = intent.getStringExtra(EXTRA_POSTER) ?: ""

        playerContainer = findViewById(R.id.easyplexContainer)
        loader = findViewById(R.id.loader)
        errorLayout = findViewById(R.id.errorLayout)
        btnRetry = findViewById(R.id.btnRetry)
        btnRetry.setOnClickListener { loadContent() }

        loadContent()
        handler.postDelayed(saveProgressRunnable, 10000)
    }

    private fun loadContent() {
        if (streamUrl.isEmpty()) { showError(); return }
        errorLayout.visibility = View.GONE
        playWithHlsPlayer()
    }

    private fun playWithHlsPlayer() {
        playerContainer.visibility = View.VISIBLE
        loader.visibility = View.VISIBLE

        hlsPlayer?.release()
        hlsPlayerView = HlsPlayerView(this)
        playerContainer.removeAllViews()
        playerContainer.addView(hlsPlayerView, ViewGroup.LayoutParams(-1, -1))

        hlsPlayer = HlsPlayer(this, hlsPlayerView!!)
        hlsPlayer!!.setListener(object : HlsPlayer.HlsPlayerExtendedListener {
            override fun onPlayerReady() = runOnUiThread {
                loader.visibility = View.GONE
                hlsPlayerView?.getPlayerController()?.setTitle(movieTitle)
            }
            override fun onPlayerError(error: String?) = runOnUiThread { showError() }
            override fun onPlaybackStateChanged(state: Int) {}
            override fun onLiveDetected(isLive: Boolean) = runOnUiThread {
                hlsPlayerView?.getPlayerController()?.setLiveTvMode(isLive)
            }
            override fun onQualityClicked() {
                val qualities = hlsPlayer?.getAvailableQualities()?.toTypedArray() ?: return
                android.app.AlertDialog.Builder(this@PlayerActivity)
                    .setTitle("Select Quality")
                    .setItems(qualities) { _, i -> hlsPlayer?.changeQuality(qualities[i]) }
                    .show()
            }
            override fun onSubtitleClicked() {}
            override fun onFullscreenClicked() {}
            override fun onPipClicked() {}
        })

        val type = if (streamUrl.lowercase().contains(".m3u8")) "hls" else "mp4"
        hlsPlayer!!.playVideo(streamUrl, type)
    }

    private fun showError() {
        loader.visibility = View.GONE
        playerContainer.visibility = View.GONE
        errorLayout.visibility = View.VISIBLE
    }

    private fun saveProgress() {
        val position = hlsPlayer?.getCurrentPosition() ?: 0L
        val duration = hlsPlayer?.getDuration() ?: 0L
        if (position <= 0 || streamUrl.isEmpty()) return
        WatchHistoryManager.save(applicationContext, WatchItem(
            contentId = contentId,
            title = movieTitle,
            poster = posterUrl,
            streamUrl = streamUrl,
            sourceType = sourceType,
            positionMs = position,
            durationMs = duration,
            timestamp = System.currentTimeMillis()
        ))
    }

    override fun onPause() { super.onPause(); hlsPlayer?.pause() }
    override fun onResume() { super.onResume(); hlsPlayer?.resume() }
    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        saveProgress()
        hlsPlayer?.release()
    }
}
