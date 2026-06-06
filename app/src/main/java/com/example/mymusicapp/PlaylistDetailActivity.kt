package com.example.mymusicapp

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlaylistDetailActivity : AppCompatActivity() {

    private lateinit var db: AppDatabase
    private val songs = mutableListOf<Song>()
    private val playlistSongs = mutableListOf<PlaylistSongEntity>()
    private var playlistId = 0
    private lateinit var adapter: SongAdapter
    private val handler = Handler(Looper.getMainLooper())

    private var shuffleOn = false
    private val shuffleQueue = mutableListOf<Int>()
    private var shuffleQueueIndex = 0

    private var musicService: MusicService? = null
    private var serviceBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val musicBinder = binder as MusicService.MusicBinder
            musicService = musicBinder.getService()
            serviceBound = true

            musicService?.onSongChanged = { index ->
                runOnUiThread {
                    findViewById<TextView>(R.id.tvSongName).text = songs.getOrNull(index)?.title ?: ""
                    startSeekBarUpdate()
                }
            }

            musicService?.onPlayStateChanged = { isPlaying ->
                runOnUiThread {
                    findViewById<ImageButton>(R.id.btnPlayPause).setImageResource(
                        if (isPlaying) android.R.drawable.ic_media_pause
                        else android.R.drawable.ic_media_play
                    )
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            serviceBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_playlist_detail)

        db = AppDatabase.getDatabase(this)
        playlistId = intent.getIntExtra("playlistId", 0)
        val playlistName = intent.getStringExtra("playlistName") ?: "Playlist"

        val serviceIntent = Intent(this, MusicService::class.java)
        startService(serviceIntent)
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)

        findViewById<TextView>(R.id.tvPlaylistName).text = playlistName
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.btnPlayPause).setOnClickListener { musicService?.togglePlayPause() }
        findViewById<ImageButton>(R.id.btnNext).setOnClickListener { playNext() }
        findViewById<ImageButton>(R.id.btnPrevious).setOnClickListener { playPrevious() }
        findViewById<ImageButton>(R.id.btnAddSong).setOnClickListener { showAddSongDialog() }
        findViewById<ImageButton>(R.id.btnShuffle).setOnClickListener { toggleShuffle() }
        findViewById<android.widget.Button>(R.id.btnPlayAll).setOnClickListener {
            if (songs.isEmpty()) return@setOnClickListener
            shuffleOn = false
            findViewById<ImageButton>(R.id.btnShuffle).setColorFilter(0xFFAAAAAA.toInt())
            musicService?.songs?.clear()
            musicService?.songs?.addAll(songs)
            musicService?.onCompletion = { playNext() }
            musicService?.playSong(0)
        }

        findViewById<android.widget.Button>(R.id.btnShuffleAll).setOnClickListener {
            if (songs.isEmpty()) return@setOnClickListener
            shuffleOn = true
            resetShuffleQueue()
            findViewById<ImageButton>(R.id.btnShuffle).setColorFilter(0xFF4a90d9.toInt())
            musicService?.songs?.clear()
            musicService?.songs?.addAll(songs)
            musicService?.onCompletion = { playNext() }
            val firstIndex = shuffleQueue[0]
            musicService?.playSong(firstIndex)
        }

        setupSeekBar()

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerViewPlaylistSongs)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = SongAdapter(
            songs,
            onClick = { index ->
                musicService?.songs?.clear()
                musicService?.songs?.addAll(songs)
                musicService?.onCompletion = { playNext() }
                musicService?.playSong(index)
                if (shuffleOn) {
                    shuffleQueueIndex = shuffleQueue.indexOf(index)
                    if (shuffleQueueIndex == -1) shuffleQueueIndex = 0
                }
            },
            onLongClick = { index -> showRemoveSongDialog(index) }
        )
        recyclerView.adapter = adapter

        loadSongs()
    }

    private fun toggleShuffle() {
        shuffleOn = !shuffleOn
        val btn = findViewById<ImageButton>(R.id.btnShuffle)
        btn.setColorFilter(if (shuffleOn) 0xFF4a90d9.toInt() else 0xFFAAAAAA.toInt())
        if (shuffleOn) resetShuffleQueue()
    }

    private fun resetShuffleQueue() {
        shuffleQueue.clear()
        val indices = (0 until songs.size).toMutableList()
        indices.shuffle()
        shuffleQueue.addAll(indices)
        shuffleQueueIndex = 0
    }

    private fun playNext() {
        if (songs.isEmpty()) return
        val currentIndex = musicService?.currentIndex ?: 0
        val nextIndex = if (shuffleOn) {
            shuffleQueueIndex++
            if (shuffleQueueIndex >= shuffleQueue.size) resetShuffleQueue()
            shuffleQueue[shuffleQueueIndex]
        } else {
            (currentIndex + 1) % songs.size
        }
        musicService?.songs?.clear()
        musicService?.songs?.addAll(songs)
        musicService?.onCompletion = { playNext() }
        musicService?.playSong(nextIndex)
    }

    private fun playPrevious() {
        if (songs.isEmpty()) return
        val currentIndex = musicService?.currentIndex ?: 0
        val prevIndex = if (shuffleOn) {
            shuffleQueueIndex--
            if (shuffleQueueIndex < 0) shuffleQueueIndex = 0
            shuffleQueue[shuffleQueueIndex]
        } else {
            if (currentIndex - 1 < 0) songs.size - 1 else currentIndex - 1
        }
        musicService?.songs?.clear()
        musicService?.songs?.addAll(songs)
        musicService?.onCompletion = { playNext() }
        musicService?.playSong(prevIndex)
    }

    private fun loadSongs() {
        CoroutineScope(Dispatchers.IO).launch {
            val result = db.playlistDao().getSongsInPlaylist(playlistId)
            withContext(Dispatchers.Main) {
                playlistSongs.clear()
                playlistSongs.addAll(result)
                songs.clear()
                songs.addAll(result.map { Song(it.songTitle, it.songArtist, it.songPath) })
                adapter.notifyDataSetChanged()
                if (shuffleOn) resetShuffleQueue()
            }
        }
    }

    private fun showAddSongDialog() {
        CoroutineScope(Dispatchers.IO).launch {
            val allSongs = mutableListOf<Song>()

            val assetFiles = assets.list("") ?: emptyArray()
            for (file in assetFiles) {
                if (file.endsWith(".mp3")) {
                    allSongs.add(Song(file.removeSuffix(".mp3"), "Default", "asset://$file"))
                }
            }

            val uri = android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            val projection = arrayOf(
                android.provider.MediaStore.Audio.Media.TITLE,
                android.provider.MediaStore.Audio.Media.ARTIST,
                android.provider.MediaStore.Audio.Media.DATA
            )
            val cursor = contentResolver.query(uri, projection, null, null, null)
            cursor?.use {
                while (it.moveToNext()) {
                    val title = it.getString(0) ?: "Unknown"
                    val artist = it.getString(1) ?: "Unknown"
                    val path = it.getString(2) ?: ""
                    if (path.isNotEmpty()) allSongs.add(Song(title, artist, path))
                }
            }

            val songNames = allSongs.map { it.title }.toTypedArray()

            withContext(Dispatchers.Main) {
                AlertDialog.Builder(this@PlaylistDetailActivity)
                    .setTitle("Add Song")
                    .setItems(songNames) { _, which ->
                        val selected = allSongs[which]
                        CoroutineScope(Dispatchers.IO).launch {
                            db.playlistDao().addSongToPlaylist(
                                PlaylistSongEntity(
                                    playlistId = playlistId,
                                    songPath = selected.path,
                                    songTitle = selected.title,
                                    songArtist = selected.artist
                                )
                            )
                            loadSongs()
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
    }

    private fun showRemoveSongDialog(index: Int) {
        AlertDialog.Builder(this)
            .setTitle("Remove Song")
            .setMessage("Remove ${songs[index].title} from playlist?")
            .setPositiveButton("Remove") { _, _ ->
                CoroutineScope(Dispatchers.IO).launch {
                    db.playlistDao().removeSongFromPlaylist(playlistSongs[index])
                    loadSongs()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setupSeekBar() {
        val seekBar = findViewById<SeekBar>(R.id.seekBar)
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    musicService?.mediaPlayer?.seekTo(progress)
                    findViewById<TextView>(R.id.tvCurrentTime).text = formatTime(progress)
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })
    }

    private fun startSeekBarUpdate() {
        handler.post(object : Runnable {
            override fun run() {
                musicService?.mediaPlayer?.let {
                    val seekBar = findViewById<SeekBar>(R.id.seekBar)
                    seekBar.progress = it.currentPosition
                    seekBar.max = it.duration
                    findViewById<TextView>(R.id.tvCurrentTime).text = formatTime(it.currentPosition)
                    findViewById<TextView>(R.id.tvTotalTime).text = formatTime(it.duration)
                }
                handler.postDelayed(this, 500)
            }
        })
    }

    private fun formatTime(ms: Int): String {
        val seconds = (ms / 1000) % 60
        val minutes = (ms / 1000) / 60
        return "%d:%02d".format(minutes, seconds)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (serviceBound) unbindService(serviceConnection)
        handler.removeCallbacksAndMessages(null)
    }
}