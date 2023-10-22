package com.github.salaink.tomcontrol.dlab

object DlabCodec {
    private fun tripleLE(value: Int) = byteArrayOf(
        value.toByte(),
        (value shr 8).toByte(),
        (value shr 16).toByte()
    )

    internal fun encodePower(powerA: Int, powerB: Int): ByteArray {
        // both inputs are 0-2047, encoded as 11 bits each.
        val combined = (powerB and 2047) or ((powerA and 2047) shl 11)
        return tripleLE(combined)
    }

    internal fun encodePattern(pattern: Pattern): ByteArray {
        val combined = (pattern.pulseDurationMs and 0x1f) or
                ((pattern.pauseDurationMs and 0x3ff) shl 5) or
                ((pattern.amplitude and 0x1f) shl 15)
        return tripleLE(combined)
    }

    data class Pattern(
        val pulseDurationMs: Int,
        val pauseDurationMs: Int,
        val amplitude: Int,
    ) {
        val durationMs: Int
            get() = pulseDurationMs + pauseDurationMs
    }
}
