package com.daftech.looplive

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class PlaylistAdapter(
    private val items: ArrayList<Uri>,
    private val nameOf: (Uri) -> String,
    private val onRemove: (Int) -> Unit
) : RecyclerView.Adapter<PlaylistAdapter.Holder>() {

    class Holder(v: View) : RecyclerView.ViewHolder(v) {
        val name: TextView = v.findViewById(R.id.tvName)
        val del: Button = v.findViewById(R.id.btnDel)
    }

    override fun onCreateViewHolder(p: ViewGroup, t: Int): Holder {
        val v = LayoutInflater.from(p.context).inflate(R.layout.item_video, p, false)
        return Holder(v)
    }

    override fun onBindViewHolder(h: Holder, pos: Int) {
        h.name.text = "${pos + 1}. ${nameOf(items[pos])}"
        h.del.setOnClickListener { onRemove(h.bindingAdapterPosition) }
    }

    override fun getItemCount() = items.size
}
