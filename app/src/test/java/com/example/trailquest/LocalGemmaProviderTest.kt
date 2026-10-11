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
import org.junit.Assert.assertThrows
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

        var recommendedThreadsBehavior: () -> Int = { 4 }

        override fun getRecommendedThreads(): Int =
            recommendedThreadsBehavior()
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
        provider.awaitCleanup()
        assertEquals(0, fakeEngine.cleanUpCallCount)
    }

    @Test
    fun setThreads_and_resetContext_participateInMutexSynchronization() = runTest {
        val fakeEngine = FakeInferenceEngine()
        val provider = LocalGemmaProvider(fakeEngine)
        provider.loadModel("model.gguf")

        provider.setThreads(6)
        assertEquals(6, fakeEngine.activeThreads)

        provider.resetContext()
        assertEquals(1, fakeEngine.resetContextCallCount)
    }

    @Test
    fun earlyStop_via_onToken_restoresModelReady_andAllowsImmediateReuse() = runTest {
        val fakeEngine = FakeInferenceEngine()
        val provider = LocalGemmaProvider(fakeEngine)
        provider.loadModel("model.gguf")

        // First transaction with early stop condition
        val earlyResult = provider.runTransaction(
            prompt = "Short prompt",
            onToken = { token, _ -> token.contains("title") }
        )
        assertTrue(earlyResult.isNotEmpty())
        assertTrue("Engine must be in ModelReady after early stop", fakeEngine.state.value is InferenceEngine.State.ModelReady)

        // Immediate subsequent transaction must succeed without deadlock or state error
        val secondResult = provider.runTransaction(prompt = "Second prompt")
        assertTrue(secondResult.isNotEmpty())
        assertTrue("Engine must be in ModelReady after second transaction", fakeEngine.state.value is InferenceEngine.State.ModelReady)
    }

    @Test(expected = IllegalStateException::class)
    fun operations_after_cleanup_are_rejected() = runTest {
        val fakeEngine = FakeInferenceEngine()
        val provider = LocalGemmaProvider(fakeEngine)
        provider.cleanup()

        provider.runTransaction(prompt = "Prompt after close")
    }

    @Test
    fun getRecommendedThreads_returnsCachedValueEvenAfterClosure() {
        val fakeEngine = FakeInferenceEngine()
        val provider = LocalGemmaProvider(fakeEngine)
        assertEquals(4, provider.getRecommendedThreads())
        provider.cleanup()
        assertTrue(provider.isClosed())
        assertEquals(4, provider.getRecommendedThreads())
    }

    @Test
    fun getRecommendedThreads_whenUnsatisfiedLinkError_fallsBackTo4() {
        val fakeEngine = FakeInferenceEngine()
        fakeEngine.recommendedThreadsBehavior = { throw UnsatisfiedLinkError("Simulated JNI link error") }
        val provider = LocalGemmaProvider(fakeEngine)

        assertEquals(4, provider.getRecommendedThreads())
    }

    @Test
    fun getRecommendedThreads_whenOutOfMemoryError_propagatesWithoutSwallowing() {
        val fakeEngine = FakeInferenceEngine()
        fakeEngine.recommendedThreadsBehavior = { throw OutOfMemoryError("Simulated OOM") }
        val provider = LocalGemmaProvider(fakeEngine)

        assertThrows(OutOfMemoryError::class.java) {
            provider.getRecommendedThreads()
        }
    }

    @Test
    fun getRecommendedThreads_whenAssertionError_propagatesWithoutSwallowing() {
        val fakeEngine = FakeInferenceEngine()
        fakeEngine.recommendedThreadsBehavior = { throw AssertionError("Simulated assertion failure") }
        val provider = LocalGemmaProvider(fakeEngine)

        assertThrows(AssertionError::class.java) {
            provider.getRecommendedThreads()
        }
    }

    @Test
    fun getRecommendedThreads_whenUnrelatedRuntimeException_propagatesWithoutSwallowing() {
        val fakeEngine = FakeInferenceEngine()
        fakeEngine.recommendedThreadsBehavior = { throw IllegalStateException("Unexpected engine failure") }
        val provider = LocalGemmaProvider(fakeEngine)

        assertThrows(IllegalStateException::class.java) {
            provider.getRecommendedThreads()
        }
    }

    @Test
    fun getRecommendedThreads_whenEngineSucceeds_returnsActualValue() {
        val fakeEngine = FakeInferenceEngine()
        fakeEngine.recommendedThreadsBehavior = { 8 }
        val provider = LocalGemmaProvider(fakeEngine)

        assertEquals(8, provider.getRecommendedThreads())
    }

    @Test
    fun repeatedAndConcurrentCleanup_runsAtMostOnce() = runTest {
        val fakeEngine = FakeInferenceEngine()
        val provider = LocalGemmaProvider(fakeEngine)
        provider.loadModel("test-model.gguf")
        assertEquals(InferenceEngine.State.ModelReady, fakeEngine.state.value)

        val j1 = launch(Dispatchers.Default) { provider.cleanup() }
        val j2 = launch(Dispatchers.Default) { provider.cleanup() }
        val j3 = launch(Dispatchers.Default) { provider.cleanup() }
        j1.join()
        j2.join()
        j3.join()
        provider.awaitCleanup()

        assertTrue(provider.isClosed())
        assertEquals("cleanUp must be called at most once", 1, fakeEngine.cleanUpCallCount)
        assertEquals(InferenceEngine.State.Initialized, fakeEngine.state.value)
    }

    @Test
    fun cleanup_inModelReadyAndErrorStates_properlyCleansUp() = runTest {
        // 1. ModelReady state cleanup
        val fakeEngine1 = FakeInferenceEngine()
        val provider1 = LocalGemmaProvider(fakeEngine1)
        provider1.loadModel("model1.gguf")
        provider1.cleanup()
        provider1.awaitCleanup()
        assertEquals(1, fakeEngine1.cleanUpCallCount)
        assertEquals(InferenceEngine.State.Initialized, fakeEngine1.state.value)

        // 2. Error state cleanup
        val fakeEngine2 = FakeInferenceEngine()
        val provider2 = LocalGemmaProvider(fakeEngine2)
        fakeEngine2.simulateError()
        provider2.cleanup()
        provider2.awaitCleanup()
        assertEquals(1, fakeEngine2.cleanUpCallCount)
        assertEquals(InferenceEngine.State.Initialized, fakeEngine2.state.value)
    }

    @Test
    fun queuedOperation_duringClosure_isRejectedWithoutTouchingEngine() = runTest {
        val fakeEngine = FakeInferenceEngine()
        fakeEngine.delayPerTokenMs = 50
        val provider = LocalGemmaProvider(fakeEngine)
        provider.loadModel("model.gguf")

        // Start active transaction holding the mutex
        val txStarted = CompletableDeferred<Unit>()
        val activeTx = launch(Dispatchers.Default) {
            txStarted.complete(Unit)
            provider.runTransaction(prompt = "Active prompt")
        }
        txStarted.await()
        delay(20)

        // Queue a second transaction while active is running
        var queuedFailedWithClosed = false
        val queuedTx = launch(Dispatchers.Default) {
            try {
                provider.runTransaction(prompt = "Queued prompt")
            } catch (e: IllegalStateException) {
                if (e.message?.contains("closed") == true) {
                    queuedFailedWithClosed = true
                }
            }
        }

        // Trigger cleanup while active is in-flight and queued is waiting for lock
        delay(10)
        provider.cleanup()
        assertTrue(provider.isClosed())

        activeTx.join()
        queuedTx.join()
        provider.awaitCleanup()

        assertTrue("Queued transaction must fail with closed state error", queuedFailedWithClosed)
        assertEquals("cleanUp must eventually execute after active finishes", 1, fakeEngine.cleanUpCallCount)
    }

    @Test
    fun generate_rechecksClosedStateAfterAcquiringMutex() = runTest {
        val fakeEngine = FakeInferenceEngine()
        fakeEngine.delayPerTokenMs = 50
        val provider = LocalGemmaProvider(fakeEngine)
        provider.loadModel("model.gguf")

        // Active transaction holding the lock
        val activeStarted = CompletableDeferred<Unit>()
        val activeTx = launch(Dispatchers.Default) {
            activeStarted.complete(Unit)
            provider.runTransaction(prompt = "Hold lock")
        }
        activeStarted.await()
        delay(20)

        // Launch generate which will wait on engineMutex
        var generateFailedWithClosed = false
        val generateJob = launch(Dispatchers.Default) {
            try {
                provider.generate("Generate prompt").collect {}
            } catch (e: IllegalStateException) {
                if (e.message?.contains("closed") == true) {
                    generateFailedWithClosed = true
                }
            }
        }

        delay(10)
        // Cleanup requested while generate is queued
        provider.cleanup()
        assertTrue(provider.isClosed())

        activeTx.join()
        generateJob.join()
        provider.awaitCleanup()

        assertTrue("Generate must fail closed check after obtaining lock", generateFailedWithClosed)
        // engine.sendUserPrompt should have only run once (for activeTx, not for generate)
        assertEquals(1, fakeEngine.resetContextCallCount)
    }
}
