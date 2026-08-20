package app.aapswear.model

/** Canonical Sugarlicious glucose Y scale shared by phone, watch and complication previews. */
object GlucoseGraphScale {
    private const val ZERO_RATIO = 0.055
    private const val LOW_RATIO = 0.215
    private const val TARGET_HIGH_RATIO = 0.515
    private const val DISPLAY_MAX = 400.0

    fun ratio(valueMgDl: Double): Double {
        val value = valueMgDl.coerceIn(0.0, DISPLAY_MAX)
        return when {
            value <= 80.0 -> ZERO_RATIO + (value / 80.0) * (LOW_RATIO - ZERO_RATIO)
            value <= 160.0 -> LOW_RATIO + ((value - 80.0) / 80.0) * (TARGET_HIGH_RATIO - LOW_RATIO)
            else -> TARGET_HIGH_RATIO + ((value - 160.0) / (DISPLAY_MAX - 160.0)) * (1.0 - TARGET_HIGH_RATIO)
        }.coerceIn(ZERO_RATIO, 1.0)
    }
}
