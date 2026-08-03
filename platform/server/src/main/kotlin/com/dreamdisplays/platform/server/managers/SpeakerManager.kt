package com.dreamdisplays.platform.server.managers

import com.dreamdisplays.platform.server.datatypes.display.SpeakerData
import com.dreamdisplays.platform.server.datatypes.display.toPacket
import com.dreamdisplays.platform.server.storage.SpeakerStore
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages the registry of registered speakers. Speakers are named sound-source points that
 * displays can bind (up to [MAX_SPEAKERS_PER_DISPLAY] per display) to route their audio through.
 * Persisted via [SpeakerStore].
 */
object SpeakerManager {
    /** Maximum number of speakers a single display may bind. */
    const val MAX_SPEAKERS_PER_DISPLAY = 10

    /** In-memory registry of all speakers, keyed by UUID. */
    private val speakers: MutableMap<UUID, SpeakerData> = ConcurrentHashMap()

    /** Restores the registry from disk. Call once at server startup. */
    fun loadFromDisk() {
        SpeakerStore.load().forEach { record ->
            SpeakerStore.toData(record)?.let { speakers[it.id] = it }
        }
    }

    /** Returns the speaker registered under [id], or null if none exists. */
    fun get(id: UUID?): SpeakerData? = id?.let { speakers[it] }

    /** Returns a snapshot list of all currently registered speakers, sorted by name. */
    fun list(): List<SpeakerData> =
        speakers.values.sortedWith(compareBy({ it.name.lowercase(Locale.ROOT) }, { it.id.toString() }))

    /** Registers a new speaker, persists it, and returns the registered instance. */
    fun register(speaker: SpeakerData): SpeakerData {
        speakers[speaker.id] = speaker
        save()
        return speaker
    }

    /** Removes the speaker referenced by [id]; returns true if one was removed. */
    fun delete(id: UUID): Boolean {
        val removed = speakers.remove(id) != null
        if (removed) save()
        return removed
    }

    /**
     * Resolves a user-supplied id token: an exact name, a full UUID, or a case-insensitive prefix
     * of the 8-character short id (`/display speaker list` column). Returns null when ambiguous
     * or unknown.
     */
    fun resolve(token: String): SpeakerData? {
        val trimmed = token.trim()
        if (trimmed.isEmpty()) return null

        speakers[trimmed.lowercase(Locale.ROOT)]?.let { return it }

        val matches = list().filter {
            it.name.equals(trimmed, ignoreCase = true) ||
                    it.id.toString().equals(trimmed, ignoreCase = true) ||
                    it.id.toString().startsWith(trimmed, ignoreCase = true)
        }
        return matches.singleOrNull()
    }

    /** Suggests short ids (8-char prefix, like `/display list`) and names for tab-completion. */
    fun suggestions(): List<String> {
        val ids = list().map { it.id.toString().substring(0, 8) }
        val names = list().map { it.name }
        return (ids + names).distinct()
    }

    /** Persists the current registry to disk. */
    fun save() {
        SpeakerStore.save(list().map(SpeakerStore::fromData))
    }
}