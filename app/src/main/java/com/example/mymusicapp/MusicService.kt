package com.example.mymusicapp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.MediaPlayer
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.support.v4.media.session.MediaSessionCompat
import androidx.core.app.NotificationCompat

class MusicService : Service() {

    private val binder = MusicBinder()
    var mediaPlayer: MediaPlayer? = null
    var songs = mutableListOf<Song>()
    var currentIndex = 0
    var onSongChanged: ((Int) -> Unit)? = null
    var onPlayStateChanged: ((Boolean) -> Unit)? = null
    var onCompletion: (() -> Unit)? = null
    private lateinit var mediaSession: MediaSessionCompat
    private val CHANNEL_ID = "music_channel"
    private val NOTIFICATION_ID = 1

    inner class MusicBinder : Binder() {
        fun getService(): MusicService = this@MusicService
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        mediaSession = MediaSessionCompat(this, "MusicService")
        mediaSession.isActive = true
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Music Player",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    fun playSong(index: Int) {
        if (songs.isEmpty()) return
        currentIndex = index
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer().apply {
            val song = songs[index]
            if (song.path.startsWith("asset://")) {
                val afd = assets.openFd(song.path.removePrefix("asset://"))
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
            } else {
                setDataSource(song.path)
            }
            prepare()
            setOnCompletionListener {
                if (onCompletion != null) {
                    onCompletion?.invoke()
                } else {
                    playNext()
                }
            }
            start()
        }
        onSongChanged?.invoke(index)
        onPlayStateChanged?.invoke(true)
        showNotification(songs[index].title, songs[index].artist)
    }

    fun togglePlayPause() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                onPlayStateChanged?.invoke(false)
            } else {
                it.start()
                onPlayStateChanged?.invoke(true)
            }
            if (songs.isNotEmpty()) {
                showNotification(songs[currentIndex].title, songs[currentIndex].artist)
            }
        }
    }

    fun playNext() {
        if (songs.isEmpty()) return
        currentIndex = (currentIndex + 1) % songs.size
        playSong(currentIndex)
    }

    fun playPrevious() {
        if (songs.isEmpty()) return
        currentIndex = if (currentIndex - 1 < 0) songs.size - 1 else currentIndex - 1
        playSong(currentIndex)
    }

    fun isPlaying() = mediaPlayer?.isPlaying == true

    private fun showNotification(title: String, artist: String) {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val prevIntent = PendingIntent.getService(
            this, 0,
            Intent(this, MusicService::class.java).setAction("PREV"),
            PendingIntent.FLAG_IMMUTABLE
        )
        val playIntent = PendingIntent.getService(
            this, 1,
            Intent(this, MusicService::class.java).setAction("PLAY_PAUSE"),
            PendingIntent.FLAG_IMMUTABLE
        )
        val nextIntent = PendingIntent.getService(
            this, 2,
            Intent(this, MusicService::class.java).setAction("NEXT"),
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(artist)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_media_previous, "Prev", prevIntent)
            .addAction(
                if (isPlaying()) android.R.drawable.ic_media_pause
                else android.R.drawable.ic_media_play,
                "Play", playIntent
            )
            .addAction(android.R.drawable.ic_media_next, "Next", nextIntent)
            .setStyle(androidx.media.app.NotificationCompat.MediaStyle()
                .setMediaSession(mediaSession.sessionToken)
                .setShowActionsInCompactView(0, 1, 2))
            .setOngoing(isPlaying())
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "PLAY_PAUSE" -> togglePlayPause()
            "NEXT" -> {
                onCompletion = null
                playNext()
            }
            "PREV" -> {
                onCompletion = null
                playPrevious()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        mediaSession.release()
    }
}