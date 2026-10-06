package com.example.trailquest

import android.content.Context
import com.arm.aichat.AiChat
import kotlinx.coroutines.flow.Flow

class LocalGemmaProvider(context: Context) : LlmProvider {
    private val engine = AiChat.getInferenceEngine(context)

    override suspend fun loadModel(path: String) {
        engine.loadModel(path)
    }

    override suspend fun setSystemPrompt(prompt: String) {
        engine.setSystemPrompt(prompt)
    }

    override fun generate(prompt: String): Flow<String> {
        return engine.sendUserPrompt(prompt)
    }

    override suspend fun benchmark(): String {
        return engine.bench(512, 128, 1, 1)
    }

    override fun cleanup() {
        engine.cleanUp()
    }
}
