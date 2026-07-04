package com.mynk.hlsplayer.player

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class WatchItem(
    val contentId: Int,
    val title: String,
    val poster: String,
    val streamUrl: String,
    val sourceType: Int,
    val positionMs: Long,
    val durationMs: Long,
    val timestamp: Long
) {
    companion object {
        fun fromJson(j: JSONObject) = WatchItem(
            contentId = j.optInt("contentId", -1),
            title = j.optString("title", ""),
            poster = j.optString("poster", ""),
            streamUrl = j.optString("streamUrl", ""),
            sourceType = j.optInt("sourceType", 8),
            positionMs = j.optLong("positionMs", 0),
            durationMs = j.optLong("durationMs", 0),
            timestamp = j.optLong("timestamp", 0)
        )
    }

    fun toJson() = JSONObject().apply {
        put("contentId", contentId)
        put("title", title)
        put("poster", poster)
        put("streamUrl", streamUrl)
        put("sourceType", sourceType)
        put("positionMs", positionMs)
        put("durationMs", durationMs)
        put("timestamp", timestamp)
    }
}

object WatchHistoryManager {

    private const val PREF_NAME = "watch_history"
    private const val KEY_LIST = "history_list"
    private const val MAX_ITEMS = 20

    fun save(context: Context, item: WatchItem) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val list = getAll(context).toMutableList()
        list.removeAll { it.contentId == item.contentId && it.streamUrl == item.streamUrl }
        list.add(0, item)
        val trimmed = list.take(MAX_ITEMS)
        val arr = JSONArray()
        trimmed.forEach { arr.put(it.toJson()) }
        prefs.edit().putString(KEY_LIST, arr.toString()).apply()
    }

    fun getAll(context: Context): List<WatchItem> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_LIST, "[]") ?: "[]"
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { WatchItem.fromJson(arr.getJSONObject(it)) }
        } catch (e: Exception) { emptyList() }
    }

    fun remove(context: Context, contentId: Int, streamUrl: String) {
        val list = getAll(context).toMutableList()
        list.removeAll { it.contentId == contentId && it.streamUrl == streamUrl }
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        prefs.edit().putString(KEY_LIST, arr.toString()).apply()
    }

    fun clearAll(context: Context) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
