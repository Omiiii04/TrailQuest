package com.example.trailquest

import com.arm.aichat.InferenceEngine
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class LocalGemmaProviderTest {

    private class FakeInferenceEngine : InferenceEngine {
        private val _state = MutableStateFlow<InferenceEngine.State>(InferenceEngine.State.Initialized)
        override val state: StateFlow<InferenceEngine.State> = _state.asStateFlow()

        var loadModelCallCount = 0
        var cleanUpCallCount = 0
        var resetContextCallCount = 0
        var activeThreads = 4
        var tokensToEmit: List<String> = listOf("{\"title\": ", "\"Walk\", ", "\"durationMinutes\": 30, ", "\"missions\": [\"Walk\"]}")
        var delayPerTokenMs = 0L
        val activeGenerations = AtomicInteger(0)
        var maxConcurrentGenerations = 0

        override suspend fun loadModel(pathToModel: String) {
            check(_state.value is InferenceEngine.State.Initialized) {
                "Cannot load model in ${_state.value}"
            }
            loadModelCallCount++
            _state.value = InferenceEngine.State.ModelReady
        }

        override suspend fun setSystemPrompt(systemPrompt: String) {
            check(_state.value is InferenceEngine.State.ModelReady)
        }

        override fun sendUserPrompt(message: String, predictLength: Int): Flow<String> = flow {
            check(_state.value is InferenceEngine.State.ModelReady) { "Cannot send prompt in ${_state.value}" }
            val current = activeGenerations.incrementAndGet()
            if (current > maxConcurrentGenerations) {
                maxConcurrentGenerations = current
            }
            _state.value = InferenceEngine.State.Generating
            try {
                for (token in tokensToEmit) {
                    if (delayPerTokenMs > 0) delay(delayPerTokenMs)
                    emit(token)
                }
                _state.value = InferenceEngine.State.ModelReady
            } finally {
                activeGenerations.decrementAndGet()
                if (_state.value is InferenceEngine.State.Generating) {
                    _state.value = InferenceEngine.State.ModelReady
                }
            }
        }

        override suspend fun bench(pp: Int, tg: Int, pl: Int, nr: Int): String {
            check(_state.value is InferenceEngine.State.ModelReady)
            return "bench_ok"
        }

        override fun cleanUp() {
            when (_state.value) {
                is InferenceEngine.State.ModelReady, is InferenceEngine.State.Error -> {
                    cleanUpCallCount++
                    _state.value = InferenceEngine.State.Initialized
                }
                else -> throw IllegalStateException("Cannot cleanUp in ${_state.value}")
            }
        }

        override fun resetContext() {
            if (_state.value is InferenceEngine.State.ModelReady) {
                resetContextCallCount++
            }
        }

        override fun setThreads(threads: Int) {
            activeThreads = threads
        }

        override fun getRecommendedThreads(): Int = 4
        override fun destroy() {}

        fun simulateError() {
            _state.value = InferenceEngine.State.Error(RuntimeException("Simulated native failure"))
        }
    }

    @Test
    fun runTransaction_atomicallyResetsAndGeneratesUnderLock() = runTest {
        val fakeEngine = FakeInferenceEngine()
        val provider = LocalGemmaProvider(fakeEngine)

        provider.loadModel("test-model.gguf")
        assertEquals(1, fakeEngine.loadModelCallCount)

        val result = provider.runTransaction(
            prompt = "Generate test",
            threads = 4,
            onToken = { token, _ -> token.contains("}") }
        )

        assertTrue("Result must contain emitted JSON", result.contains("\"durationMinutes\": 30"))
        assertEquals(1, fakeEngine.resetContextCallCount)
        assertEquals(4, fakeEngine.activeThreads)
        assertTrue(fakeEngine.state.value is InferenceEngine.State.ModelReady)
    }

    @Test
    fun runTransaction_serializesConcurrentRequests() = runTest {
        val fakeEngine = FakeInferenceEngine()
        fakeEngine.delayPerTokenMs = 20
        val provider = LocalGemmaProvider(fakeEngine)
        provider.loadModel("test-model.gguf")

        val job1 = launch(Dispatchers.Default) {
            provider.runTransaction(prompt = "Req 1")
        }
        val job2 = launch(Dispatchers.Default) {
            provider.runTransaction(prompt = "Req 2")
        }

        job1.join()
        job2.join()

        // Max concurrent generations on engine must NEVER exceed 1
        assertEquals("Transactions must never run concurrently on the engine", 1, fakeEngine.maxConcurrentGenerations)
        assertEquals(2, fakeEngine.resetContextCallCount)
    }

    @Test
    fun runTransaction_cancellationReleasesLockAndLeavesModelReady() = runTest {
        val fakeEngine = FakeInferenceEngine()
        fakeEngine.delayPerTokenMs = 100
        val provider = LocalGemmaProvider(fakeEngine)
        provider.loadModel("test-model.gguf")

        val started = CompletableDeferred<Unit>()
        val job = launch(Dispatchers.Default) {
            started.complete(Unit)
            provider.runTransaction(prompt = "Long prompt")
        }

        started.await()
        delay(50)
        job.cancel()
        job.join()

        // Engine must be restored to ModelReady
        assertTrue("Engine must return to ModelReady on cancellation", fakeEngine.state.value is InferenceEngine.State.ModelReady)

        // Subsequent transaction must acquire the lock immediately without deadlock
        fakeEngine.delayPerTokenMs = 0
        val nextResult = provider.runTransaction(prompt = "Next prompt")
        assertTrue(nextResult.isNotBlank())
    }

    @Test
    fun loadModel_isIdempotentWhenAlreadyLoaded() = runTest {
        val fakeEngine = FakeInferenceEngine()
        val provider = LocalGemmaProvider(fakeEngine)

        provider.loadModel("model.gguf")
        assertEquals(1, fakeEngine.loadModelCallCount)

        // Second call with same path must be no-op
        provider.loadModel("model.gguf")
        assertEquals("Subsequent loadModel with same path must not reload", 1, fakeEngine.loadModelCallCount)
    }

    @Test
    fun loadModel_recoversFromErrorState() = runTest {
        val fakeEngine = FakeInferenceEngine()
        val provider = LocalGemmaProvider(fakeEngine)

        provider.loadModel("model.gguf")
        assertEquals(1, fakeEngine.loadModelCallCount)

        // Simulate native error
        fakeEngine.simulateError()
        assertTrue(fakeEngine.state.value is InferenceEngine.State.Error)

        // Retrying loadModel must cleanUp and re-initialize
        provider.loadModel("model.gguf")
        assertEquals("cleanUp must be called to reset error state", 1, fakeEngine.cleanUpCallCount)
        assertEquals("Model must be reloaded", 2, fakeEngine.loadModelCallCount)
        assertTrue(fakeEngine.state.value is InferenceEngine.State.ModelReady)
    }

    @Test
    fun cleanup_isEventualSafeAndIdempotent() = runTest {
        val fakeEngine = FakeInferenceEngine()
        val provider = LocalGemmaProvider(fakeEngine)

        // Cleanup before loading must NOT call engine.cleanUp() and must NOT throw
        provider.cleanup()
        delay(50)
        assertEquals(0, fakeEngine.cleanUpCallCount)
    }
}
