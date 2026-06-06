package com.example.mymusicapp

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.ImageButton
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlaylistActivity : AppCompatActivity() {

    private lateinit var db: AppDatabase
    private val playlists = mutableListOf<PlaylistEntity>()
    private lateinit var adapter: PlaylistAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_playlist)

        db = AppDatabase.getDatabase(this)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.btnAddPlaylist).setOnClickListener { showCreatePlaylistDialog() }

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerViewPlaylists)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = PlaylistAdapter(
            playlists,
            onClick = { playlist ->
                val intent = Intent(this, PlaylistDetailActivity::class.java)
                intent.putExtra("playlistId", playlist.id)
                intent.putExtra("playlistName", playlist.name)
                startActivity(intent)
            },
            onDelete = { playlist ->
                CoroutineScope(Dispatchers.IO).launch {
                    db.playlistDao().deletePlaylist(playlist)
                    loadPlaylists()
                }
            }
        )
        recyclerView.adapter = adapter

        loadPlaylists()
    }

    override fun onResume() {
        super.onResume()
        loadPlaylists()
    }

    private fun loadPlaylists() {
        CoroutineScope(Dispatchers.IO).launch {
            val result = db.playlistDao().getAllPlaylists()
            withContext(Dispatchers.Main) {
                playlists.clear()
                playlists.addAll(result)
                adapter.notifyDataSetChanged()
            }
        }
    }

    private fun showCreatePlaylistDialog() {
        val editText = EditText(this)
        editText.hint = "Playlist name"

        AlertDialog.Builder(this)
            .setTitle("New Playlist")
            .setView(editText)
            .setPositiveButton("Create") { _, _ ->
                val name = editText.text.toString().trim()
                if (name.isNotEmpty()) {
                    CoroutineScope(Dispatchers.IO).launch {
                        db.playlistDao().insertPlaylist(PlaylistEntity(name = name))
                        loadPlaylists()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}