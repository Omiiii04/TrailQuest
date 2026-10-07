package com.example.trailquest

import android.content.Context
import com.arm.aichat.AiChat
import kotlinx.coroutines.flow.Flow

class LocalGemmaProvider(context: Context) : LlmProvider {
    private val engine = AiChat.getInferenceEngine(context)

    // ponytail: 256 tokens is plenty for a short JSON response; raise if generation is cut short
    private val GENERATE_MAX_TOKENS = 256

    override suspend fun loadModel(path: String) {
        engine.loadModel(path)
    }

    override suspend fun setSystemPrompt(prompt: String) {
        engine.setSystemPrompt(prompt)
    }

    override fun generate(prompt: String): Flow<String> {
        return engine.sendUserPrompt(prompt, GENERATE_MAX_TOKENS)
    }

    override suspend fun benchmark(): String {
        return engine.bench(512, 128, 1, 1)
    }

    override fun resetContext() {
        engine.resetContext()
    }

    override fun setThreads(threads: Int) {
        engine.setThreads(threads)
    }

    override fun getRecommendedThreads(): Int {
        return engine.getRecommendedThreads()
    }

    override fun cleanup() {
        engine.cleanUp()
    }
}
