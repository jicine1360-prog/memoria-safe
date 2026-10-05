package com.memoria.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.memoria.R
import com.memoria.data.model.Event

class EventAdapter : RecyclerView.Adapter<EventAdapter.ViewHolder>() {

    private val items = mutableListOf<Event>()

    fun submitList(events: List<Event>) {
        items.clear()
        items.addAll(events)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_event, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val title: TextView = view.findViewById(R.id.eventTitle)
        private val detail: TextView = view.findViewById(R.id.eventDetail)

        fun bind(event: Event) {
            title.text = "${event.type} (${event.status.name})"
            detail.text = event.description
        }
    }
}