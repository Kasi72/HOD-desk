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

    suspend fun <T> use(modelPath: String, block: (Engine) -> T): T = mutex.withLock {
        releaseJob?.cancel()
        try {
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
        val file = File(modelPath)
        val key = "$modelPath|${file.length()}|${file.lastModified()}"
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
