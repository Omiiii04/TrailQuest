package com.example.trailquest

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class ModelInstaller(private val context: Context) {
    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress

    private val _status = MutableStateFlow("Initializing...")
    val status: StateFlow<String> = _status

    suspend fun installModel(assetName: String): String = withContext(Dispatchers.IO) {
        val modelsDir = File(context.filesDir, "models")
        if (!modelsDir.exists()) modelsDir.mkdirs()

        val modelFile = File(modelsDir, assetName)

        // Use AssetFileDescriptor for reliable file size (available() lies for compressed/large assets)
        val afd = context.assets.openFd("models/$assetName")
        val totalBytes = afd.length
        afd.close()

        // Skip copy only when file exists AND has the correct size (guards against partial copies)
        if (modelFile.exists() && modelFile.length() == totalBytes) {
            _progress.value = 1f
            _status.value = "Model ready"
            return@withContext modelFile.absolutePath
        }
        // Remove any partial / corrupt prior copy before starting fresh
        if (modelFile.exists()) modelFile.delete()

        _status.value = "Copying local model..."
        val inputStream = context.assets.open("models/$assetName")
        var copiedBytes = 0L
        var lastEmitMs = 0L

        // ponytail: 2 MB buffer; shrink to 512 KB if low-RAM devices OOM during copy
        val buffer = ByteArray(2 * 1024 * 1024)
        var read: Int

        inputStream.use { input ->
            FileOutputStream(modelFile).use { output ->
                while (input.read(buffer).also { read = it } != -1) {
                    output.write(buffer, 0, read)
                    copiedBytes += read
                    if (totalBytes > 0) {
                        // Throttle Compose recompositions: emit at most once per 150 ms
                        val now = SystemClock.elapsedRealtime()
                        if (now - lastEmitMs >= 150L) {
                            _progress.value = copiedBytes.toFloat() / totalBytes.toFloat()
                            lastEmitMs = now
                        }
                    }
                }
            }
        }

        _status.value = "Verifying model..."
        if (totalBytes > 0 && modelFile.length() != totalBytes) {
            modelFile.delete()
            throw Exception("Model file size mismatch after copy")
        }

        _progress.value = 1f
        _status.value = "AI ready"
        return@withContext modelFile.absolutePath
    }
}
