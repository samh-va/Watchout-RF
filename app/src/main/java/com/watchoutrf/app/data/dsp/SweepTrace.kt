package com.watchoutrf.app.data.dsp

/**
 * Post-processing of a stitched sweep: fills the bins no hop wrote to, smooths the
 * trace, and builds the partial frames shown while a sweep is still in progress.
 */
object SweepTrace {

    /** Marker for a display bin that no hop has written yet. */
    const val EMPTY = -300f

    /**
     * Gap-fills (linear interpolation across [EMPTY] bins) and applies a 3-bin moving
     * average to the first [count] bins of [raw]. [raw] itself is not modified.
     */
    fun finish(raw: FloatArray, count: Int = raw.size): FloatArray {
        val n = count.coerceIn(0, raw.size)
        val filled = raw.copyOf(n)

        var gapStart = -1
        for (i in 0 until n) {
            if (filled[i] <= EMPTY + 0.1f) {
                if (gapStart < 0) gapStart = i
            } else if (gapStart >= 0) {
                val leftVal = if (gapStart > 0) filled[gapStart - 1] else filled[i]
                val rightVal = filled[i]
                val gapLen = i - gapStart
                for (g in 0 until gapLen) {
                    val t = (g + 1).toFloat() / (gapLen + 1).toFloat()
                    filled[gapStart + g] = leftVal + (rightVal - leftVal) * t
                }
                gapStart = -1
            }
        }
        if (gapStart >= 0) {
            val leftVal = if (gapStart > 0) filled[gapStart - 1] else -100f
            for (g in gapStart until n) filled[g] = leftVal
        }

        val smoothed = FloatArray(n)
        for (i in 0 until n) {
            val left = if (i > 0) filled[i - 1] else filled[i]
            val right = if (i < n - 1) filled[i + 1] else filled[i]
            smoothed[i] = (left + filled[i] + right) / 3f
        }
        return smoothed
    }

    /**
     * Frame to display while a sweep is under way. The first [coveredBins] bins show
     * this sweep's data, blended with [previousAverage] exactly as the averager will
     * do when the sweep completes; the remaining bins keep the previous sweep's
     * values (or [floorDbm] when there is no previous sweep).
     */
    fun preview(
        raw: FloatArray,
        coveredBins: Int,
        previousAverage: FloatArray?,
        alpha: Float,
        floorDbm: Float,
    ): FloatArray {
        val n = raw.size
        val covered = coveredBins.coerceIn(0, n)
        val previous = previousAverage?.takeIf { it.size == n }
        val out = previous?.copyOf() ?: FloatArray(n) { floorDbm }
        if (covered == 0) return out

        val fresh = finish(raw, covered)
        for (i in 0 until covered) {
            out[i] = if (previous != null) previous[i] * alpha + fresh[i] * (1f - alpha) else fresh[i]
        }
        return out
    }
}
