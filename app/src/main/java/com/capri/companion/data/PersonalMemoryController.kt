
package com.capri.companion.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID

class PersonalMemoryController(context: Context) {
    private val store = PersonalMemoryStore(context)
    private val mutex = Mutex()

    private val _memories = MutableStateFlow<List<PersonalMemory>>(emptyList())
    val memories: StateFlow<List<PersonalMemory>> = _memories

    companion object {
        val CATEGORIES = setOf(
            "preference",
            "project",
            "goal",
            "date",
            "follow_up",
            "other",
            "sensitive"
        )
    }

    suspend fun refresh() = mutex.withLock {
        val loaded = withContext(Dispatchers.IO) {
            store.load()
        }
        _memories.value = loaded
    }

    suspend fun add(
        category: String,
        content: String,
        sensitiveApproved: Boolean = false
    ): PersonalMemory = mutex.withLock {
        val normalizedCategory = category.trim().lowercase()
        val cleanContent = content.trim()

        require(normalizedCategory in CATEGORIES) {
            "Categoría de recuerdo no válida."
        }

        require(cleanContent.isNotEmpty() && cleanContent.length <= 500) {
            "El recuerdo debe tener entre 1 y 500 caracteres."
        }

        require(
            normalizedCategory != "sensitive" || sensitiveApproved
        ) {
            "Necesito tu permiso antes de guardar información sensible."
        }

        val current = _memories.value
        require(current.size < PersonalMemoryStore.MAX_MEMORIES) {
            "La memoria está llena."
        }

        val now = System.currentTimeMillis()
        val memory = PersonalMemory(
            id = UUID.randomUUID().toString(),
            category = normalizedCategory,
            content = cleanContent,
            createdAt = now,
            updatedAt = now
        )

        val updated = current + memory

        withContext(Dispatchers.IO) {
            store.save(updated)
        }

        _memories.value = updated
        memory
    }

    suspend fun update(
        id: String,
        content: String
    ) = mutex.withLock {
        val cleanContent = content.trim()

        require(cleanContent.isNotEmpty() && cleanContent.length <= 500) {
            "El recuerdo debe tener entre 1 y 500 caracteres."
        }

        val current = _memories.value
        val index = current.indexOfFirst { it.id == id }

        require(index >= 0) {
            "No se encontró ese recuerdo."
        }

        val updated = current.toMutableList()
        updated[index] = updated[index].copy(
            content = cleanContent,
            updatedAt = System.currentTimeMillis()
        )

        withContext(Dispatchers.IO) {
            store.save(updated)
        }

        _memories.value = updated
    }

    suspend fun delete(id: String) = mutex.withLock {
        val updated = _memories.value.filterNot { it.id == id }

        withContext(Dispatchers.IO) {
            store.save(updated)
        }

        _memories.value = updated
    }

    suspend fun clear() = mutex.withLock {
        withContext(Dispatchers.IO) {
            store.erase()
        }

        _memories.value = emptyList()
    }
}
