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

class MainActivity : AppCompatActivity() {

    private lateinit var listView: ListView

    private val videoUris = mutableListOf<Uri>()
    private val displayNames = mutableListOf<String>()

    private lateinit var adapter: ArrayAdapter<String>

    private val pickVideos = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> }  // 保留原始声明，但不再使用

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
        setContentView(R.layout.activity_main)

        listView = findViewById(R.id.videoListView)
        val addButton = findViewById<Button>(R.id.addButton)

        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, displayNames)
        listView.adapter = adapter

        addButton.setOnClickListener {
            loadAllVideos()   // 改为刷新列表
        }

        listView.setOnItemClickListener { _, _, position, _ ->
            val intent = Intent(this, PlayerActivity::class.java)
            intent.putExtra("video_uri", videoUris[position].toString())
            intent.putExtra("current_index", position)
            intent.putStringArrayListExtra("video_list", ArrayList(videoUris.map { it.toString() }))
            startActivity(intent)
        }

        listView.setOnItemLongClickListener { _, _, position, _ ->
            AlertDialog.Builder(this@MainActivity)
                .setTitle("删除视频")
                .setMessage("确定删除该视频？")
                .setPositiveButton("删除") { _, _ ->
                    if (position >= 0 && position < videoUris.size) {
                        videoUris.removeAt(position)
                        displayNames.removeAt(position)
                        adapter.notifyDataSetChanged()
                        Toast.makeText(this@MainActivity, "已删除", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("取消", null)
                .show()
            true
        }

        checkAndRequestPermissions()
    }

    private fun checkAndRequestPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        if (permissions.any { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }) {
            requestPermission.launch(permissions)
        } else {
            loadAllVideos()
        }
    }

    private fun loadAllVideos() {
        videoUris.clear()
        displayNames.clear()

        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME
        )

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

                val contentUri = Uri.withAppendedPath(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id.toString())

                videoUris.add(contentUri)
                displayNames.add(name)
            }
        }

        adapter.notifyDataSetChanged()

        if (displayNames.isEmpty()) {
            Toast.makeText(this, "未找到视频文件", Toast.LENGTH_SHORT).show()
        }
    }
}
