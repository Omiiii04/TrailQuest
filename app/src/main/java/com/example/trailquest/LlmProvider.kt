package com.example.trailquest

import kotlinx.coroutines.flow.Flow

interface LlmProvider {
    suspend fun loadModel(path: String)
    suspend fun setSystemPrompt(prompt: String)
    fun generate(prompt: String): Flow<String>
    suspend fun benchmark(): String
    fun cleanup()
}
