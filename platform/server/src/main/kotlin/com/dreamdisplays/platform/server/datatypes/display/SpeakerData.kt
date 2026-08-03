package com.dreamdisplays.platform.server.datatypes.display

import com.dreamdisplays.core.protocol.SpeakerInfo
import java.util.*

/**
 * A registered speaker: a named sound-source point in a world. Displays can bind up to
 * [com.dreamdisplays.platform.server.managers.SpeakerManager.MAX_SPEAKERS_PER_DISPLAY] speakers and
 * route their audio through them.
 */
class SpeakerData(
    val id: UUID,
    var name: String,
    val world: String,
    val x: Int,
    val y: Int,
    val z: Int,
    var radius: Float = 16f,
)

/** Converts this speaker to its wire [SpeakerInfo] counterpart for client sync. */
fun SpeakerData.toPacket(): SpeakerInfo = SpeakerInfo(
    id = id, name = name, world = world, x = x, y = y, z = z, radius = radius,
)