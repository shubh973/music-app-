package com.example.mymusicapp

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private val allSongs = mutableListOf<Song>()
    private val deviceSongs = mutableListOf<Song>()
    private val songs = mutableListOf<Song>()
    private val filteredSongs = mutableListOf<Song>()
    private lateinit var db: AppDatabase
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
                    findViewById<TextView>(R.id.tvSongName).text = filteredSongs.getOrNull(index)?.title ?: ""
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
        setContentView(R.layout.activity_main)

        db = AppDatabase.getDatabase(this)

        val serviceIntent = Intent(this, MusicService::class.java)
        startService(serviceIntent)
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)

        requestPermission()

        findViewById<ImageButton>(R.id.btnPlayPause).setOnClickListener {
            musicService?.togglePlayPause()
        }
        findViewById<ImageButton>(R.id.btnNext).setOnClickListener { playNext() }
        findViewById<ImageButton>(R.id.btnPrevious).setOnClickListener { playPrevious() }
        findViewById<ImageButton>(R.id.btnShuffle).setOnClickListener { toggleShuffle() }

        findViewById<android.widget.Button>(R.id.btnAll).setOnClickListener {
            songs.clear()
            songs.addAll(allSongs)
            filterSongs(getCurrentSearchQuery())
            updateTabButtons(true)
            resetShuffleQueue()
        }

        findViewById<android.widget.Button>(R.id.btnMySongs).setOnClickListener {
            songs.clear()
            songs.addAll(deviceSongs)
            filterSongs(getCurrentSearchQuery())
            updateTabButtons(false)
            resetShuffleQueue()
        }

        findViewById<android.widget.Button>(R.id.btnPlaylist).setOnClickListener {
            val intent = Intent(this, PlaylistActivity::class.java)
            startActivity(intent)
        }

        setupSeekBar()
        setupSearch()
    }

    private fun getCurrentSearchQuery(): String {
        return findViewById<EditText>(R.id.etSearch).text.toString()
    }

    private fun setupSearch() {
        findViewById<EditText>(R.id.etSearch).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterSongs(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun filterSongs(query: String) {
        filteredSongs.clear()
        if (query.isEmpty()) {
            filteredSongs.addAll(songs)
        } else {
            filteredSongs.addAll(songs.filter {
                it.title.contains(query, ignoreCase = true) ||
                        it.artist.contains(query, ignoreCase = true)
            })
        }
        findViewById<RecyclerView>(R.id.recyclerView).adapter?.notifyDataSetChanged()
        musicService?.songs?.clear()
        musicService?.songs?.addAll(filteredSongs)
    }

    private fun toggleShuffle() {
        shuffleOn = !shuffleOn
        val btn = findViewById<ImageButton>(R.id.btnShuffle)
        btn.setColorFilter(if (shuffleOn) 0xFF4a90d9.toInt() else 0xFFAAAAAA.toInt())
        if (shuffleOn) resetShuffleQueue()
    }

    private fun resetShuffleQueue() {
        shuffleQueue.clear()
        val indices = (0 until filteredSongs.size).toMutableList()
        indices.shuffle()
        shuffleQueue.addAll(indices)
        shuffleQueueIndex = 0
    }

    private fun updateTabButtons(allSelected: Boolean) {
        findViewById<android.widget.Button>(R.id.btnAll)
            .setBackgroundColor(if (allSelected) 0xFF1a3a6b.toInt() else 0xFF0d1f3c.toInt())
        findViewById<android.widget.Button>(R.id.btnMySongs)
            .setBackgroundColor(if (!allSelected) 0xFF1a3a6b.toInt() else 0xFF0d1f3c.toInt())
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

    private fun requestPermission() {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val notGranted = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (notGranted.isEmpty()) {
            loadSongs()
        } else {
            ActivityCompat.requestPermissions(this, notGranted.toTypedArray(), 1)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1) loadSongs()
    }

    private fun loadAssetSongs() {
        try {
            val assetFiles = assets.list("") ?: return
            for (file in assetFiles) {
                if (file.endsWith(".mp3")) {
                    val title = file.removeSuffix(".mp3")
                    val song = Song(title, "Default", "asset://$file")
                    allSongs.add(song)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadSongs() {
        loadAssetSongs()

        try {
            val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            val projection = arrayOf(
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.DATA
            )
            val cursor = contentResolver.query(uri, projection, null, null, null)
            cursor?.use {
                while (it.moveToNext()) {
                    val title = it.getString(0) ?: "Unknown"
                    val artist = it.getString(1) ?: "Unknown"
                    val path = it.getString(2) ?: ""
                    if (path.isNotEmpty()) {
                        val song = Song(title, artist, path)
                        deviceSongs.add(song)
                        allSongs.add(song)
                    }
                }
            }

            songs.addAll(allSongs)
            filteredSongs.addAll(allSongs)

            val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
            recyclerView.layoutManager = LinearLayoutManager(this)
            recyclerView.adapter = SongAdapter(
                filteredSongs,
                onClick = { index ->
                    musicService?.songs?.clear()
                    musicService?.songs?.addAll(filteredSongs)
                    musicService?.playSong(index)
                    if (shuffleOn) {
                        shuffleQueueIndex = shuffleQueue.indexOf(index)
                        if (shuffleQueueIndex == -1) shuffleQueueIndex = 0
                    }
                    findViewById<TextView>(R.id.tvSongName).text = filteredSongs[index].title
                    startSeekBarUpdate()
                },
                onLongClick = { index ->
                    showRenameDialog(index)
                }
            )

            musicService?.songs?.clear()
            musicService?.songs?.addAll(filteredSongs)

            CoroutineScope(Dispatchers.IO).launch {
                songs.forEachIndexed { i, song ->
                    val entity = db.songDao().getSong(song.path)
                    if (entity != null) {
                        songs[i] = songs[i].copy(title = entity.customName)
                    }
                }
                withContext(Dispatchers.Main) {
                    filteredSongs.clear()
                    filteredSongs.addAll(songs)
                    recyclerView.adapter?.notifyDataSetChanged()
                }
            }

        } catch (e: Exception) {
            findViewById<TextView>(R.id.tvSongName).text = "Error: ${e.message}"
        }
    }

    private fun showRenameDialog(index: Int) {
        val editText = EditText(this)
        editText.setText(filteredSongs[index].title)

        AlertDialog.Builder(this)
            .setTitle("Rename Song")
            .setView(editText)
            .setPositiveButton("Save") { _, _ ->
                val newName = editText.text.toString().trim()
                if (newName.isNotEmpty()) {
                    filteredSongs[index] = filteredSongs[index].copy(title = newName)
                    findViewById<RecyclerView>(R.id.recyclerView).adapter?.notifyItemChanged(index)
                    CoroutineScope(Dispatchers.IO).launch {
                        db.songDao().insertSong(SongEntity(filteredSongs[index].path, newName))
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun playNext() {
        if (filteredSongs.isEmpty()) return
        val currentIndex = musicService?.currentIndex ?: 0
        val nextIndex = if (shuffleOn) {
            shuffleQueueIndex++
            if (shuffleQueueIndex >= shuffleQueue.size) resetShuffleQueue()
            shuffleQueue[shuffleQueueIndex]
        } else {
            (currentIndex + 1) % filteredSongs.size
        }
        musicService?.playSong(nextIndex)
        findViewById<TextView>(R.id.tvSongName).text = filteredSongs[nextIndex].title
        startSeekBarUpdate()
    }

    private fun playPrevious() {
        if (filteredSongs.isEmpty()) return
        val currentIndex = musicService?.currentIndex ?: 0
        val prevIndex = if (shuffleOn) {
            shuffleQueueIndex--
            if (shuffleQueueIndex < 0) shuffleQueueIndex = 0
            shuffleQueue[shuffleQueueIndex]
        } else {
            if (currentIndex - 1 < 0) filteredSongs.size - 1 else currentIndex - 1
        }
        musicService?.playSong(prevIndex)
        findViewById<TextView>(R.id.tvSongName).text = filteredSongs[prevIndex].title
        startSeekBarUpdate()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (serviceBound) unbindService(serviceConnection)
        handler.removeCallbacksAndMessages(null)
    }
}