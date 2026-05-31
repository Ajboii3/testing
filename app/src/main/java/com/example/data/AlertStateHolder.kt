package com.example.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AlertStateHolder {
    private val _recentEvents = MutableStateFlow<List<PaymentEvent>>(emptyList())
    val recentEvents: StateFlow<List<PaymentEvent>> = _recentEvents.asStateFlow()

    private val _isServiceActive = MutableStateFlow(false)
    val isServiceActive: StateFlow<Boolean> = _isServiceActive.asStateFlow()

    private val _isListenerBound = MutableStateFlow(false)
    val isListenerBound: StateFlow<Boolean> = _isListenerBound.asStateFlow()

    fun addEvent(event: PaymentEvent) {
        val currentList = _recentEvents.value.toMutableList()
        currentList.add(0, event) // Insert at top
        if (currentList.size > 20) {
            currentList.removeLast() // Limit memory footprint
        }
        _recentEvents.value = currentList
    }

    fun setServiceActive(active: Boolean) {
        _isServiceActive.value = active
    }

    fun setListenerBound(bound: Boolean) {
        _isListenerBound.value = bound
    }

    fun clearEvents() {
        _recentEvents.value = emptyList()
    }
}
