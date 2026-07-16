package com.watchoutrf.app.domain.model

/**
 * Predefined frequency ranges for common use cases.
 */
data class FrequencyRange(
    val startHz: Long,
    val endHz: Long,
    val name: String,
) {
    val spanHz: Long get() = endHz - startHz
    val centerHz: Long get() = (startHz + endHz) / 2

    fun formatStart(): String = formatFrequency(startHz)
    fun formatEnd(): String = formatFrequency(endHz)
    fun formatSpan(): String = formatFrequency(spanHz)

    companion object {
        val FM_BROADCAST = FrequencyRange(88_000_000L, 108_000_000L, "FM Broadcast")
        val VHF_TV = FrequencyRange(174_000_000L, 230_000_000L, "VHF TV")
        val UHF_TV = FrequencyRange(470_000_000L, 862_000_000L, "UHF TV")
        val IEM_RANGE = FrequencyRange(470_000_000L, 698_000_000L, "IEM/Wireless Mic")
        val FULL_RANGE = FrequencyRange(24_000_000L, 1_766_000_000L, "Full Range")
        val CELLULAR_700 = FrequencyRange(698_000_000L, 806_000_000L, "Cellular 700")
        val ISM_900 = FrequencyRange(902_000_000L, 928_000_000L, "ISM 900")

        val PRESETS = listOf(FM_BROADCAST, VHF_TV, UHF_TV, IEM_RANGE, CELLULAR_700, ISM_900, FULL_RANGE)

        fun formatFrequency(hz: Long): String {
            return when {
                hz >= 1_000_000_000L -> String.format("%.1f GHz", hz / 1e9)
                hz >= 1_000_000L -> String.format("%.1f MHz", hz / 1e6)
                hz >= 1_000L -> String.format("%.1f kHz", hz / 1e3)
                else -> "$hz Hz"
            }
        }
    }
}
