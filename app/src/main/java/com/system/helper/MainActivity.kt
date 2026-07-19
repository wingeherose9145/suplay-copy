package com.system.helper

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class MainActivity : AppCompatActivity() {

    private lateinit var listView: ListView
    private val videoUris = mutableListOf<Uri>()
    private val displayNames = mutableListOf<String>()
    private lateinit var adapter: ArrayAdapter<String>

    private val pickVideos = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNullOrEmpty()) return@registerForActivityResult

        uris.forEach { uri ->
            if (!videoUris.contains(uri)) {
                try {
                    contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                    videoUris.add(uri)
                    displayNames.add(getFileNameFromUri(uri))
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        adapter.notifyDataSetChanged()
        saveList()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        listView = findViewById(R.id.videoListView)
        val addButton = findViewById<Button>(R.id.addButton)

        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, displayNames)
        listView.adapter = adapter

        loadSavedList()

        addButton.setOnClickListener {
            pickVideos.launch(arrayOf("video/*"))
        }

        // 单击播放，长按删除
        listView.setOnItemClickListener { _, _, position, _ ->
            playVideo(position)
        }

        listView.setOnItemLongClickListener { _, _, position, _ ->
            deleteVideo(position)
            true
        }
    }

    private fun playVideo(startIndex: Int) {
        val intent = Intent(this, PlayerActivity::class.java)
        val listStrings = ArrayList(videoUris.map { it.toString() })
        intent.putStringArrayListExtra("video_list", listStrings)
        intent.putExtra("current_index", startIndex)
        startActivity(intent)
    }

    private fun deleteVideo(position: Int) {
        if (position < 0 || position >= videoUris.size) return

        val uri = videoUris[position]
        val name = displayNames[position]

        try {
            contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: Exception) {}

        videoUris.removeAt(position)
        displayNames.removeAt(position)
        adapter.notifyDataSetChanged()
        saveList()

        Toast.makeText(this, "已删除: $name", Toast.LENGTH_SHORT).show()
    }

    private fun saveList() {
        val prefs = getSharedPreferences("app_data", MODE_PRIVATE)
        val uriStrings = videoUris.map { it.toString() }
        prefs.edit().putString("saved_uris", Gson().toJson(uriStrings)).apply()
        prefs.edit().putString("saved_names", Gson().toJson(displayNames)).apply()
    }

    private fun loadSavedList() {
        val prefs = getSharedPreferences("app_data", MODE_PRIVATE)
        val uriJson = prefs.getString("saved_uris", null) ?: return
        val nameJson = prefs.getString("saved_names", null) ?: return

        val type = object : TypeToken<List<String>>() {}.type
        val savedUris: List<String> = Gson().fromJson(uriJson, type)
        val savedNames: List<String> = Gson().fromJson(nameJson, type)

        videoUris.clear()
        displayNames.clear()
        savedUris.forEach { videoUris.add(Uri.parse(it)) }
        displayNames.addAll(savedNames)
        adapter.notifyDataSetChanged()
    }

    private fun getFileNameFromUri(uri: Uri): String {
        return contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst()) cursor.getString(nameIndex) else "未知视频"
        } ?: "未知视频"
    }
}
