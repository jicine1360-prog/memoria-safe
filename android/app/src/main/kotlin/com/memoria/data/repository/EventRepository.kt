package com.memoria.data.repository

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.memoria.data.model.Event
import com.memoria.data.model.EventFilter
import java.util.*

class EventRepository(context: Context) {

    private val store = mutableListOf<Event>()
    private val _events = MutableLiveData<List<Event>>(emptyList())

    fun getAllEvents(): LiveData<List<Event>> = _events

    fun getEventsByFilter(filter: EventFilter): LiveData<List<Event>> {
        val filtered = when (filter) {
            EventFilter.ALL -> store.toList()
            EventFilter.EMERGENCY -> store.filter { it.status == com.memoria.data.model.EventStatus.EMERGENCY }
            EventFilter.SAFETY -> store.filter { it.status == com.memoria.data.model.EventStatus.SAFE }
        }
        return MutableLiveData(filtered)
    }

    fun addEvent(event: Event) {
        store.add(0, event)
        _events.value = store.toList()
    }

    fun clearAllEvents() {
        store.clear()
        _events.value = emptyList()
    }

    fun getLastEvent(): LiveData<Event?> {
        return MutableLiveData(store.firstOrNull())
    }
}