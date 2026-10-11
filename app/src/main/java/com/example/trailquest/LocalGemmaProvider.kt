package com.example.trailquest

import android.content.Context
import android.util.Log
import com.arm.aichat.AiChat
import com.arm.aichat.InferenceEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean

class LocalGemmaProvider internal constructor(
    private val engine: InferenceEngine,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : LlmProvider {
    constructor(context: Context) : this(AiChat.getInferenceEngine(context))

    private val engineMutex = Mutex()
    private val providerScope = CoroutineScope(ioDispatcher + SupervisorJob())
    private var loadedModelPath: String? = null
    private val isClosed = AtomicBoolean(false)
    @Volatile
    private var cleanupJob: Job? = null

    internal suspend fun awaitCleanup() {
        cleanupJob?.join()
    }

    // ponytail: 256 tokens is plenty for a short JSON response; raise if generation is cut short
    private val GENERATE_MAX_TOKENS = 256

    fun isClosed(): Boolean = isClosed.get()

    override suspend fun loadModel(path: String) = engineMutex.withLock {
        check(!isClosed.get()) { "LocalGemmaProvider is closed" }
        if (loadedModelPath == path && engine.state.value is InferenceEngine.State.ModelReady) {
            return@withLock
        }
        try {
            if (engine.state.value is InferenceEngine.State.Error) {
                engine.cleanUp() // Resets state from Error back to Initialized
            } else if (engine.state.value is InferenceEngine.State.ModelReady) {
                engine.cleanUp() // Unloads previous model back to Initialized
            }
            engine.loadModel(path)
            loadedModelPath = path
        } catch (e: Exception) {
            loadedModelPath = null
            throw e
        }
    }

    override suspend fun setSystemPrompt(prompt: String) = engineMutex.withLock {
        check(!isClosed.get()) { "LocalGemmaProvider is closed" }
        check(engine.state.value is InferenceEngine.State.ModelReady) {
            "Cannot set system prompt in ${engine.state.value}"
        }
        engine.setSystemPrompt(prompt)
    }

    override fun generate(prompt: String): Flow<String> = flow {
        check(!isClosed.get()) { "LocalGemmaProvider is closed" }
        engineMutex.lock()
        try {
            check(!isClosed.get()) { "LocalGemmaProvider is closed" }
            engine.sendUserPrompt(prompt, GENERATE_MAX_TOKENS).collect { token ->
                emit(token)
            }
        } finally {
            if (engine.state.value is InferenceEngine.State.Generating) {
                engine.resetContext()
            }
            engineMutex.unlock()
        }
    }

    override suspend fun runTransaction(
        prompt: String,
        systemPrompt: String?,
        threads: Int?,
        onToken: ((token: String, accumulated: String) -> Boolean)?
    ): String = engineMutex.withLock {
        check(!isClosed.get()) { "LocalGemmaProvider is closed" }
        check(engine.state.value is InferenceEngine.State.ModelReady) {
            "Inference engine is not in ModelReady state: ${engine.state.value}"
        }

        if (threads != null) {
            engine.setThreads(threads)
        }

        engine.resetContext()

        if (!systemPrompt.isNullOrBlank()) {
            engine.setSystemPrompt(systemPrompt)
        }

        var accumulated = ""
        try {
            engine.sendUserPrompt(prompt, GENERATE_MAX_TOKENS).collect { token ->
                accumulated += token
                if (onToken != null && onToken(token, accumulated)) {
                    throw JsonCompleteException()
                }
            }
        } catch (_: JsonCompleteException) {
            // Early-stop condition satisfied; flow cancelled and state restored to ModelReady
        } finally {
            if (engine.state.value is InferenceEngine.State.Generating) {
                engine.resetContext()
            }
        }
        return@withLock accumulated
    }

    override suspend fun benchmark(): String = engineMutex.withLock {
        check(!isClosed.get()) { "LocalGemmaProvider is closed" }
        check(engine.state.value is InferenceEngine.State.ModelReady) {
            "Cannot run benchmark in ${engine.state.value}"
        }
        engine.bench(512, 128, 1, 1)
    }

    override suspend fun resetContext() = engineMutex.withLock {
        check(!isClosed.get()) { "LocalGemmaProvider is closed" }
        if (engine.state.value is InferenceEngine.State.ModelReady) {
            engine.resetContext()
        }
    }

    override suspend fun setThreads(threads: Int) = engineMutex.withLock {
        check(!isClosed.get()) { "LocalGemmaProvider is closed" }
        if (engine.state.value is InferenceEngine.State.ModelReady) {
            engine.setThreads(threads)
        }
    }

    override fun getRecommendedThreads(): Int {
        return try {
            engine.getRecommendedThreads()
        } catch (_: Throwable) {
            4
        }
    }

    override fun cleanup() {
        if (!isClosed.compareAndSet(false, true)) return
        cleanupJob = providerScope.launch {
            engineMutex.withLock {
                try {
                    if (engine.state.value is InferenceEngine.State.ModelReady ||
                        engine.state.value is InferenceEngine.State.Error) {
                        engine.cleanUp()
                    }
                    loadedModelPath = null
                } catch (e: Exception) {
                    Log.e("LocalGemmaProvider", "Error during engine cleanup", e)
                } finally {
                    providerScope.cancel()
                }
            }
        }
    }
}
