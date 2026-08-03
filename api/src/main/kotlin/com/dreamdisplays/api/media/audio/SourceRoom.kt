package com.dreamdisplays.api.media.audio

import com.dreamdisplays.api.DreamDisplaysUnstableApi

/**
 * A room: a sphere in world space, used to gate a source's audio when [SourceAcousticState.roomConfined]
 * is true. A listener inside any of a source's rooms hears it; a listener outside every room is
 * hard-muted at the DSP gate (see the `:media:audio` engine).
 *
 * One block is treated as one meter.
 *
 * @since 1.9.0
 */
@DreamDisplaysUnstableApi
data class SourceRoom(
    val centerX: Double,
    val centerY: Double,
    val centerZ: Double,
    val radius: Double,
)