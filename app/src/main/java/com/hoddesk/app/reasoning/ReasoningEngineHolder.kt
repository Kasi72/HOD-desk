package com.hoddesk.app.reasoning

import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/**
 * Keeps one LiteRT-LM engine loaded between review items instead of reloading a multi-gigabyte model
 * for every letter. Calls are serialised, a failed engine is never reused, a replaced model file gets a
 * fresh engine, and the engine is closed after a quiet period so the model does not stay in memory.
 */
object ReasoningEngineHolder {
    private const val TAG = "HOD_REASONING"
    private const val IDLE_RELEASE_MS = 120_000L

    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var engine: Engine? = null
    private var engineKey: String? = null
    private var releaseJob: Job? = null
    private var useGeneration = 0L

    /**
     * @param memory when given, checked before loading; a refusal throws [InsufficientMemoryException]
     * and, if Android reports low memory, frees an already-loaded engine too.
     */
    suspend fun <T> use(modelPath: String, memory: (() -> MemorySnapshot)? = null, block: (Engine) -> T): T = mutex.withLock {
        releaseJob?.cancel()
        try {
            memory?.let { probe ->
                val file = File(modelPath)
                val loaded = engine != null && engineKey == keyFor(modelPath, file)
                val snapshot = probe()
                val decision = ReasoningMemoryPolicy.decide(snapshot, file.length(), loaded)
                if (!decision.allowed) {
                    Log.w(TAG, "engine_load_refused avail=${snapshot.availableBytes} low=${snapshot.lowMemory}")
                    throw InsufficientMemoryException(snapshot, decision.requiredBytes)
                }
            }
            block(acquireLocked(modelPath))
        } catch (error: Throwable) {
            closeLocked()
            throw error
        } finally {
            scheduleRelease()
        }
    }

    /** Frees the model now, e.g. before the model file is replaced. */
    suspend fun release() = mutex.withLock {
        releaseJob?.cancel()
        closeLocked()
    }

    private fun acquireLocked(modelPath: String): Engine {
        val key = keyFor(modelPath, File(modelPath))
        engine?.let { if (engineKey == key) return it }
        closeLocked()
        val fresh = Engine(EngineConfig(modelPath, Backend.CPU()))
        try {
            fresh.initialize()
        } catch (error: Throwable) {
            runCatching { fresh.close() }
            throw error
        }
        engine = fresh
        engineKey = key
        Log.i(TAG, "engine_loaded")
        return fresh
    }

    private fun keyFor(modelPath: String, file: File) = "$modelPath|${file.length()}|${file.lastModified()}"

    /** Called when Android asks the app to give memory back. Waits for any running inference. */
    fun releaseAsync() { scope.launch { release() } }

    private fun closeLocked() {
        engine?.let { runCatching { it.close() }; Log.i(TAG, "engine_released") }
        engine = null
        engineKey = null
    }

    private fun scheduleRelease() {
        if (engine == null) return
        val generation = ++useGeneration
        releaseJob = scope.launch {
            delay(IDLE_RELEASE_MS)
            // Only release if no newer use has happened since this timer was set.
            mutex.withLock { if (generation == useGeneration) closeLocked() }
        }
    }
}
