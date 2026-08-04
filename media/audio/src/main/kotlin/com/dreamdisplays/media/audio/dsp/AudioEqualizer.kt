package com.dreamdisplays.media.audio.dsp

import kotlin.math.tan

/**
 * 10-band graphic equalizer using a cascade of peaking/shelf biquad filters.
 * Band center frequencies: 31Hz, 62Hz, 125Hz, 250Hz, 500Hz, 1kHz, 2kHz, 4kHz, 8kHz, 16kHz.
 */
class AudioEqualizer(private val sampleRate: Float = 44100f) {
    companion object {
        val FREQUENCIES = floatArrayOf(31f, 62f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)
        const val BANDS = 10

        enum class Preset(val gains: FloatArray) {
            FLAT(floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)),
            BASS_BOOST(floatArrayOf(6f, 5f, 4f, 2f, 0f, 0f, 0f, 0f, 0f, 0f)),
            VOCAL_CLARITY(floatArrayOf(-2f, -1f, 0f, 2f, 4f, 4f, 3f, 1f, 0f, -1f)),
            CINEMA(floatArrayOf(4f, 3f, 1f, 0f, -1f, 0f, 2f, 3f, 4f, 2f)),
            NIGHT_MODE(floatArrayOf(-4f, -3f, -2f, 0f, 1f, 2f, 1f, -1f, -3f, -5f)),
        }
    }

    private val filtersL = Array(BANDS) { Biquad() }
    private val filtersR = Array(BANDS) { Biquad() }
    val gainsDb = FloatArray(BANDS) { 0f }

    init {
        updateCoefficients()
    }

    fun setBandGain(band: Int, gainDb: Float) {
        if (band !in 0 until BANDS) return
        gainsDb[band] = gainDb.coerceIn(-12f, 12f)
        updateBand(band)
    }

    fun applyPreset(preset: Preset) {
        for (i in 0 until BANDS) {
            gainsDb[i] = preset.gains[i]
            updateBand(i)
        }
    }

    private fun updateBand(band: Int) {
        val freq = FREQUENCIES[band]
        val db = gainsDb[band]
        val q = 1.414f
        filtersL[band].setPeakingEQ(sampleRate, freq, q, db)
        filtersR[band].setPeakingEQ(sampleRate, freq, q, db)
    }

    private fun updateCoefficients() {
        for (i in 0 until BANDS) {
            updateBand(i)
        }
    }

    fun reset() {
        for (i in 0 until BANDS) {
            filtersL[i].reset()
            filtersR[i].reset()
        }
    }

    fun processStereo(buffer: ByteArray, bytesRead: Int) {
        val sampleCount = bytesRead / 4
        for (i in 0 until sampleCount) {
            val off = i * 4
            var l = ((buffer[off].toInt() and 0xFF) or (buffer[off + 1].toInt() shl 8)).toShort().toFloat() / 32768f
            var r = ((buffer[off + 2].toInt() and 0xFF) or (buffer[off + 3].toInt() shl 8)).toShort().toFloat() / 32768f

            for (b in 0 until BANDS) {
                if (gainsDb[b] != 0f) {
                    l = filtersL[b].process(l)
                    r = filtersR[b].process(r)
                }
            }

            val iL = (l.coerceIn(-1f, 1f) * 32767f).toInt().toShort()
            val iR = (r.coerceIn(-1f, 1f) * 32767f).toInt().toShort()

            buffer[off] = (iL.toInt() and 0xFF).toByte()
            buffer[off + 1] = ((iL.toInt() shr 8) and 0xFF).toByte()
            buffer[off + 2] = (iR.toInt() and 0xFF).toByte()
            buffer[off + 3] = ((iR.toInt() shr 8) and 0xFF).toByte()
        }
    }
}
