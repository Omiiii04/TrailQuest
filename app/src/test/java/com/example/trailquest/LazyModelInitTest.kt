package com.example.trailquest

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LazyModelInitTest {

    private class FakeLlmProvider : LlmProvider {
        var isCleanedUp = false
        var loadModelCallCount = 0

        override suspend fun loadModel(path: String) {
            loadModelCallCount++
        }

        override suspend fun setSystemPrompt(prompt: String) {}
        override fun generate(prompt: String): Flow<String> = emptyFlow()
        override suspend fun benchmark(): String = "bench"
        override fun resetContext() {}
        override fun setThreads(threads: Int) {}
        override fun getRecommendedThreads(): Int = 4
        override fun cleanup() {
            isCleanedUp = true
        }
    }

    private class ProviderLifecycleOwner {
        private var _provider: FakeLlmProvider? = null
        var factoryCallCount = 0

        fun getOrCreateProvider(): FakeLlmProvider {
            if (_provider == null) {
                factoryCallCount++
                _provider = FakeLlmProvider()
            }
            return _provider!!
        }

        fun hasInstantiatedProvider(): Boolean = _provider != null

        fun cleanup() {
            _provider?.cleanup()
        }
    }

    @Test
    fun lazyLifecycleOwner_doesNotInstantiateProviderUntilRequested() {
        val owner = ProviderLifecycleOwner()

        // When application starts and mounts Home / Setup / Review
        assertFalse("Provider must not be instantiated on Home/Setup/Review paths", owner.hasInstantiatedProvider())
        assertEquals(0, owner.factoryCallCount)

        // Cleanup before initialization must be completely safe and idempotent
        owner.cleanup()
        assertFalse(owner.hasInstantiatedProvider())
    }

    @Test
    fun lazyLifecycleOwner_instantiatesOnlyOnceAndReusesInstance() {
        val owner = ProviderLifecycleOwner()

        // Visiting Developer Tools route triggers the provider factory
        val instance1 = owner.getOrCreateProvider()
        assertNotNull(instance1)
        assertEquals(1, owner.factoryCallCount)
        assertTrue(owner.hasInstantiatedProvider())

        // Recompositions or re-visiting Developer Tools reuses the existing instance
        val instance2 = owner.getOrCreateProvider()
        assertSame("Subsequent accesses must reuse the identical single instance", instance1, instance2)
        assertEquals(1, owner.factoryCallCount)

        // Cleanup cleans up the instance
        owner.cleanup()
        assertTrue(instance1.isCleanedUp)
    }
}
