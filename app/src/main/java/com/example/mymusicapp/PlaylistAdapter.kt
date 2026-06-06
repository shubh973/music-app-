package com.example.mymusicapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class PlaylistAdapter(
    private val playlists: List<PlaylistEntity>,
    private val onClick: (PlaylistEntity) -> Unit,
    private val onDelete: (PlaylistEntity) -> Unit
) : RecyclerView.Adapter<PlaylistAdapter.PlaylistViewHolder>() {

    inner class PlaylistViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.tvPlaylistName)
        val btnDelete: ImageButton = view.findViewById(R.id.btnDeletePlaylist)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlaylistViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.playlist_item, parent, false)
        return PlaylistViewHolder(view)
    }

    override fun onBindViewHolder(holder: PlaylistViewHolder, position: Int) {
        holder.name.text = playlists[position].name
        holder.itemView.setOnClickListener { onClick(playlists[position]) }
        holder.btnDelete.setOnClickListener { onDelete(playlists[position]) }
    }

    override fun getItemCount() = playlists.size
}