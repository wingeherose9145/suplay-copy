package com.system.helper

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class MainActivity : AppCompatActivity() {

    private lateinit var listView: ListView
    private val videoUris = mutableListOf<Uri>()
    private val displayNames = mutableListOf<String>()
    private lateinit var adapter: ArrayAdapter<String>
    private lateinit var addButton: Button

    private val requestPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            loadAllVideos()
        } else {
            Toast.makeText(this, "需要权限才能读取视频", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 如果已有保存的视频列表，则直接进入随机播放
        if (hasSavedVideoList()) {
            startRandomPlayback()
            return
        }

        // 第一次使用：显示主界面让用户添加视频
        setContentView(R.layout.activity_main)

        listView = findViewById(R.id.videoListView)
        addButton = findViewById(R.id.addButton)

        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, displayNames)
        listView.adapter = adapter

        addButton.text = "添加视频"
        addButton.setOnClickListener { addVideos() }

        // 点击播放
        listView.setOnItemClickListener { _, _, position, _ ->
            startPlayerActivity(position)
        }

        // 长按删除
        listView.setOnItemLongClickListener { _, _, position, _ ->
            if (position >= 0 && position < videoUris.size) {
                AlertDialog.Builder(this)
                    .setTitle("删除")
                    .setMessage("从列表中移除？")
                    .setPositiveButton("移除") { _, _ ->
                        videoUris.removeAt(position)
                        displayNames.removeAt(position)
                        adapter.notifyDataSetChanged()
                        saveVideoList()
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
            true
        }

        // 首次启动请求权限
        if (!hasPermission()) {
            requestPermission()
        }
    }

    private fun hasSavedVideoList(): Boolean {
        val prefs = getSharedPreferences("video_list", MODE_PRIVATE)
        return prefs.contains("uris")
    }

    private fun startRandomPlayback() {
        val intent = Intent(this, PlayerActivity::class.java)
        intent.putStringArrayListExtra("video_list", ArrayList(loadSavedUris()))
        startActivity(intent)
        finish() // 关闭 MainActivity
    }

    private fun loadSavedUris(): List<String> {
        val prefs = getSharedPreferences("video_list", MODE_PRIVATE)
        val json = prefs.getString("uris", null) ?: return emptyList()
        return try {
            Gson().fromJson(json, object : TypeToken<List<String>>() {}.type)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun addVideos() {
        if (hasPermission()) {
            loadAllVideos()
        } else {
            requestPermission()
        }
    }

    private fun hasPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun requestPermission() {
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        requestPermission.launch(perm)
    }

    private fun loadAllVideos() {
        videoUris.clear()
        displayNames.clear()

        val projection = arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME)

        contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            "${MediaStore.Video.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn) ?: "未知视频"
                val uri = Uri.withAppendedPath(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id.toString())

                videoUris.add(uri)
                displayNames.add(name)
            }
        }

        adapter.notifyDataSetChanged()
        saveVideoList()

        Toast.makeText(this, "已添加 ${displayNames.size} 个视频", Toast.LENGTH_SHORT).show()
    }

    private fun saveVideoList() {
        val prefs = getSharedPreferences("video_list", MODE_PRIVATE)
        val editor = prefs.edit()
        editor.putString("uris", Gson().toJson(videoUris.map { it.toString() }))
        editor.putString("names", Gson().toJson(displayNames))
        editor.apply()
    }

    private fun startPlayerActivity(position: Int) {
        val intent = Intent(this, PlayerActivity::class.java).apply {
            putExtra("video_uri", videoUris[position].toString())
            putExtra("current_index", position)
            putStringArrayListExtra("video_list", ArrayList(videoUris.map { it.toString() }))
        }
        startActivity(intent)
    }
}
