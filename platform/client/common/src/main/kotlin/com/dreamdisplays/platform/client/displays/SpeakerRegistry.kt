package com.dreamdisplays.platform.client.displays

import com.dreamdisplays.core.protocol.SpeakerInfo
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Client-side registry of the speakers pushed by the server via [com.dreamdisplays.core.protocol.SpeakerList].
 * Speakers are world-filtered by the server, so every entry here is treated as being in the local
 * player's current world; the registry is replaced wholesale whenever a new list arrives.
 */
object SpeakerRegistry {
    /** Speakers keyed by id. */
    private val speakers = ConcurrentHashMap<UUID, SpeakerInfo>()

    /** Replaces the whole registry with [entries]. */
    fun update(entries: List<SpeakerInfo>) {
        speakers.clear()
        entries.forEach { speakers[it.id] = it }
    }

    /** Clears the registry (e.g. on level change / disconnect). */
    fun clear() {
        speakers.clear()
    }

    /** Returns the speaker registered under [id], or null. */
    fun get(id: UUID): SpeakerInfo? = speakers[id]

    /** Returns a snapshot of all registered speakers, sorted by name. */
    fun all(): List<SpeakerInfo> =
        speakers.values.sortedBy { it.name.lowercase() }

    /** Returns true if [id] refers to a currently registered speaker. */
    fun contains(id: UUID): Boolean = speakers.containsKey(id)
}
