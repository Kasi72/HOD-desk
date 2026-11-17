package com.hoddesk.app.reasoning

import android.app.ActivityManager
import android.content.Context

/** Free memory as Android reports it at one moment. */
data class MemorySnapshot(val availableBytes: Long, val totalBytes: Long, val lowMemory: Boolean)

/** Raised before the model is loaded when the phone cannot spare the memory; nothing is half-loaded. */
class InsufficientMemoryException(val snapshot: MemorySnapshot, val requiredBytes: Long) :
    IllegalStateException("Not enough free memory to load the on-device model")

/**
 * Decides whether the instruction model may be loaded now. The thresholds are a deliberate, simple
 * heuristic: the runtime maps the model file, so not all of it is resident, but it needs a large
 * working set plus headroom so the rest of the phone is not squeezed. When the answer is no, Review
 * keeps the on-device keyword extraction it already shows.
 */
object ReasoningMemoryPolicy {
    private const val MB = 1024L * 1024L
    const val HEADROOM_BYTES = 768L * MB
    const val MIN_WORKING_SET_BYTES = 1024L * MB

    data class Decision(val allowed: Boolean, val requiredBytes: Long, val reason: String?)

    fun requiredBytes(modelBytes: Long): Long = maxOf(MIN_WORKING_SET_BYTES, modelBytes / 2) + HEADROOM_BYTES

    fun decide(snapshot: MemorySnapshot, modelBytes: Long, alreadyLoaded: Boolean): Decision {
        val required = requiredBytes(modelBytes)
        return when {
            snapshot.lowMemory -> Decision(false, required, "Android reports the phone is low on memory")
            // A loaded engine already holds its memory; reusing it costs nothing extra.
            alreadyLoaded -> Decision(true, required, null)
            snapshot.availableBytes < required -> Decision(false, required, "Only ${gb(snapshot.availableBytes)} free; about ${gb(required)} is needed")
            else -> Decision(true, required, null)
        }
    }

    fun gb(bytes: Long): String = String.format(java.util.Locale.ENGLISH, "%.1f GB", bytes / (1024.0 * MB))

    fun snapshot(context: Context): MemorySnapshot {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val info = ActivityManager.MemoryInfo().also { manager?.getMemoryInfo(it) }
        return MemorySnapshot(info.availMem, info.totalMem, info.lowMemory)
    }
}
