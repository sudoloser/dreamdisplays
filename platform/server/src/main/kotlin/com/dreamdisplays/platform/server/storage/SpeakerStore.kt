package com.dreamdisplays.platform.server.storage

import com.dreamdisplays.api.storage.SpeakerRecord
import com.dreamdisplays.platform.server.datatypes.display.SpeakerData
import com.dreamdisplays.util.json.JsonFileStore
import kotlinx.serialization.builtins.ListSerializer
import org.slf4j.LoggerFactory
import java.util.*

/**
 * JSON persistence for the registered speakers registry, so speakers survive a server restart.
 * Modeled on [PipPinStore]: flat, no per-server keying.
 */
object SpeakerStore {
    private val logger = LoggerFactory.getLogger("DreamDisplays/SpeakerStore")
    private const val SCHEMA_VERSION = 1
    private const val FILE_NAME = "speakers.json"
    private val jsonFiles = JsonFileStore()
    private val listSerializer = ListSerializer(SpeakerRecord.serializer())

    /** Loads every persisted speaker record, or an empty list if none are saved yet. */
    fun load(): List<SpeakerRecord> =
        jsonFiles.readVersioned(jsonFiles.file(FILE_NAME), listSerializer, SCHEMA_VERSION, logger) ?: emptyList()

    /** Overwrites the store with [records]. */
    fun save(records: List<SpeakerRecord>) {
        if (!jsonFiles.ensureDir(logger)) return
        jsonFiles.writeVersioned(jsonFiles.file(FILE_NAME), listSerializer, records, SCHEMA_VERSION, logger)
    }

    /** Converts a [SpeakerRecord] back into a registry-friendly [SpeakerData]. */
    fun toData(record: SpeakerRecord): SpeakerData? =
        runCatching { UUID.fromString(record.id) }
            .getOrNull()
            ?.let { id -> SpeakerData(id, record.name, record.world, record.x, record.y, record.z, record.radius) }

    /** Converts a [SpeakerData] into its serializable [SpeakerRecord]. */
    fun fromData(data: SpeakerData): SpeakerRecord =
        SpeakerRecord(data.id.toString(), data.name, data.world, data.x, data.y, data.z, data.radius)
}