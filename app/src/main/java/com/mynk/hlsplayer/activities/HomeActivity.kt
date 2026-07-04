package com.mynk.hlsplayer.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mynk.hlsplayer.R

class HomeActivity : AppCompatActivity() {

    private lateinit var etUrl: EditText
    private lateinit var btnClear: ImageView
    private lateinit var btnPlay: LinearLayout
    private lateinit var btnClearAll: TextView
    private lateinit var rvRecent: RecyclerView
    private lateinit var emptyState: LinearLayout

    private val recentKey = "recent_urls"
    private val recentList = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        etUrl = findViewById(R.id.etStreamUrl)
        btnClear = findViewById(R.id.btnClear)
        btnPlay = findViewById(R.id.btnPlay)
        btnClearAll = findViewById(R.id.btnClearAll)
        rvRecent = findViewById(R.id.rvRecent)
        emptyState = findViewById(R.id.emptyState)

        loadRecent()
        setupRecyclerView()

        etUrl.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                btnClear.visibility = if (s.isNullOrEmpty()) View.GONE else View.VISIBLE
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        btnClear.setOnClickListener { etUrl.setText("") }

        btnPlay.setOnClickListener { playUrl() }

        etUrl.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) { playUrl(); true } else false
        }

        btnClearAll.setOnClickListener {
            recentList.clear()
            saveRecent()
            setupRecyclerView()
        }
    }

    private var isLaunching = false

    private fun playUrl() {
        val url = etUrl.text.toString().trim()
        if (url.isEmpty()) { Toast.makeText(this, "Enter a stream URL", Toast.LENGTH_SHORT).show(); return }
        if (isLaunching) return
        isLaunching = true
        saveToRecent(url)
        startActivity(Intent(this, PlayerActivity::class.java).apply {
            putExtra(PlayerActivity.EXTRA_URL, url)
            putExtra(PlayerActivity.EXTRA_TITLE, extractTitleFromUrl(url))
            putExtra(PlayerActivity.EXTRA_TYPE, 1)
        })
    }

    override fun onResume() {
        super.onResume()
        isLaunching = false
    }

    private fun extractTitleFromUrl(url: String): String {
        return try {
            val path = java.net.URL(url).path
            val segment = path.substringAfterLast("/").ifEmpty { url.substringAfterLast("/") }
            segment.substringBeforeLast(".")
                .replace(Regex("[-_]"), " ")
                .replaceFirstChar { it.uppercase() }
                .ifEmpty { "Stream" }
        } catch (e: Exception) { "Stream" }
    }

    private fun saveToRecent(url: String) {
        recentList.remove(url)
        recentList.add(0, url)
        if (recentList.size > 20) recentList.removeLast()
        saveRecent()
        setupRecyclerView()
    }

    private fun setupRecyclerView() {
        if (recentList.isEmpty()) {
            rvRecent.visibility = View.GONE
            emptyState.visibility = View.VISIBLE
        } else {
            rvRecent.visibility = View.VISIBLE
            emptyState.visibility = View.GONE
            rvRecent.layoutManager = LinearLayoutManager(this)
            rvRecent.adapter = RecentAdapter(recentList,
                onPlay = { url -> etUrl.setText(url); playUrl() },
                onDelete = { url -> recentList.remove(url); saveRecent(); setupRecyclerView() }
            )
        }
    }

    private fun loadRecent() {
        val prefs = getSharedPreferences(recentKey, Context.MODE_PRIVATE)
        val saved = prefs.getString("list", "") ?: ""
        recentList.clear()
        if (saved.isNotEmpty()) recentList.addAll(saved.split("|||").filter { it.isNotEmpty() })
    }

    private fun saveRecent() {
        getSharedPreferences(recentKey, Context.MODE_PRIVATE).edit()
            .putString("list", recentList.joinToString("|||")).apply()
    }

    class RecentAdapter(
        private val items: List<String>,
        private val onPlay: (String) -> Unit,
        private val onDelete: (String) -> Unit
    ) : RecyclerView.Adapter<RecentAdapter.VH>() {

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val tvUrl: TextView = view.findViewById(R.id.tvRecentUrl)
            val btnDelete: ImageView = view.findViewById(R.id.btnDeleteRecent)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_recent_url, parent, false))

        override fun onBindViewHolder(holder: VH, position: Int) {
            val url = items[position]
            holder.tvUrl.text = url
            holder.itemView.setOnClickListener { onPlay(url) }
            holder.btnDelete.setOnClickListener { onDelete(url) }
        }

        override fun getItemCount() = items.size
    }
}
