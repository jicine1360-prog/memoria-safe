package com.memoria.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.memoria.data.model.Event
import com.memoria.data.model.EventFilter
import com.memoria.data.model.EventStatus
import com.memoria.data.repository.EventRepository
import java.util.*

class EmergencyViewModel(application: Application) : AndroidViewModel(application) {

    private val eventRepository = EventRepository(application.applicationContext)

    private val _filter = MutableLiveData(EventFilter.ALL)
    val filter: LiveData<EventFilter> = _filter

    private val _events = MutableLiveData<List<Event>>(emptyList())
    val events: LiveData<List<Event>> = _events

    private var latestEvents: List<Event> = emptyList()

    private val _audioLevel = MutableLiveData(0f)
    val audioLevel: LiveData<Float> = _audioLevel

    init {
        eventRepository.getAllEvents().observeForever { list ->
            latestEvents = list
            publish()
        }
        _filter.observeForever {
            publish()
        }
    }

    private fun publish() {
        val filter = _filter.value ?: EventFilter.ALL
        _events.value = when (filter) {
            EventFilter.ALL -> latestEvents
            EventFilter.EMERGENCY -> latestEvents.filter { it.status == EventStatus.EMERGENCY }
            EventFilter.SAFETY -> latestEvents.filter { it.status == EventStatus.SAFE }
        }
    }

    fun setFilter(filter: EventFilter) {
        _filter.value = filter
    }

    fun updateAudioLevel(level: Float) {
        _audioLevel.value = level
    }

    fun addEvent(event: Event) {
        eventRepository.addEvent(event)
    }

    fun clearAllEvents() {
        eventRepository.clearAllEvents()
    }

    fun createEmergencyEvent(): Event {
        val event = Event(
            id = UUID.randomUUID().toString(),
            timestamp = Date(),
            status = EventStatus.EMERGENCY,
            type = "SOS_ACTIVATED",
            location = null,
            description = "Emergency activated via SOS button"
        )

        addEvent(event)
        return event
    }
}