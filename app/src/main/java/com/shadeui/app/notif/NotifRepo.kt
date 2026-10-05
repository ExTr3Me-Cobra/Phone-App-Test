package com.shadeui.app.notif

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The current notifications, in the system's ranking order. Updated by [NotifListener]. */
class NotifRepo {
    private val state = MutableStateFlow<List<NotifItem>>(emptyList())
    val flow: StateFlow<List<NotifItem>> = state.asStateFlow()
    val value: List<NotifItem> get() = state.value

    fun replaceAll(items: List<NotifItem>) {
        state.value = items
    }

    fun upsert(item: NotifItem, order: List<String>) {
        val map = state.value.associateBy { it.key }.toMutableMap()
        map[item.key] = item
        state.value = sortByOrder(map.values, order)
    }

    fun remove(key: String) {
        state.value = state.value.filterNot { it.key == key }
    }

    fun reorder(order: List<String>, importanceByKey: Map<String, Int>) {
        state.value = sortByOrder(
            state.value.map { it.copy(importance = importanceByKey[it.key] ?: it.importance) },
            order,
        )
    }

    private fun sortByOrder(items: Collection<NotifItem>, order: List<String>): List<NotifItem> {
        val rank = order.withIndex().associate { it.value to it.index }
        return items.sortedWith(compareBy<NotifItem> { rank[it.key] ?: Int.MAX_VALUE }.thenByDescending { it.time })
    }
}
