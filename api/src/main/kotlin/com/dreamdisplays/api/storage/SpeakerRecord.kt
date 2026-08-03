package com.dreamdisplays.api.storage

import kotlinx.serialization.Serializable

/**
 * Persisted record of one registered speaker: a named sound-source point in a world.
 */
@Serializable
data class SpeakerRecord(
    val id: String,
    val name: String,
    val world: String,
    val x: Int,
    val y: Int,
    val z: Int,
    val radius: Float = 16f,
)