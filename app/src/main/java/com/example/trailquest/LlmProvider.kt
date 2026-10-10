package com.example.trailquest

import kotlinx.coroutines.flow.Flow

interface LlmProvider {
    suspend fun loadModel(path: String)
    suspend fun setSystemPrompt(prompt: String)
    fun generate(prompt: String): Flow<String>
    suspend fun benchmark(): String
    fun resetContext()
    fun setThreads(threads: Int)
    fun getRecommendedThreads(): Int
    fun cleanup()

    /**
     * Executes an atomic inference transaction under exclusive engine lock.
     * Guarantees context reset, prompt application, and stream collection
     * cannot be interleaved by another thread, benchmark, or model reload.
     */
    suspend fun runTransaction(
        prompt: String,
        systemPrompt: String? = null,
        threads: Int? = null,
        onToken: ((token: String, accumulated: String) -> Boolean)? = null
    ): String
}
