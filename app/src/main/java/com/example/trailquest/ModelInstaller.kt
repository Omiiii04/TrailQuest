package com.example.trailquest

import android.content.Context
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
        if (!modelsDir.exists()) {
            modelsDir.mkdirs()
        }

        val modelFile = File(modelsDir, assetName)
        if (modelFile.exists()) {
            _progress.value = 1f
            _status.value = "Model ready"
            return@withContext modelFile.absolutePath
        }

        _status.value = "Copying local model..."
        val assetManager = context.assets
        val inputStream = assetManager.open("models/\$assetName")
        val totalBytes = inputStream.available().toLong()
        var copiedBytes = 0L
        
        val outputStream = FileOutputStream(modelFile)
        val buffer = ByteArray(8 * 1024)
        var read: Int

        inputStream.use { input ->
            outputStream.use { output ->
                while (input.read(buffer).also { read = it } != -1) {
                    output.write(buffer, 0, read)
                    copiedBytes += read
                    if (totalBytes > 0) {
                        _progress.value = copiedBytes.toFloat() / totalBytes.toFloat()
                    }
                }
            }
        }
        
        _status.value = "Verifying model..."
        if (totalBytes > 0 && modelFile.length() != totalBytes) {
            modelFile.delete()
            throw Exception("Model file size mismatch")
        }

        _progress.value = 1f
        _status.value = "AI ready"
        return@withContext modelFile.absolutePath
    }
}
