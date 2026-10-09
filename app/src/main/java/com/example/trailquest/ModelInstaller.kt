package com.example.trailquest

import android.content.Context
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class ModelInstaller(private val context: Context) {
    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress

    private val _status = MutableStateFlow("Initializing...")
    val status: StateFlow<String> = _status

    private fun cleanStaleTempFiles(modelsDir: File) {
        try {
            modelsDir.listFiles()?.forEach { file ->
                if (file.name.endsWith(".tmp")) {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            Log.w("TrailQuest/Storage", "Failed to clean stale temp files", e)
        }
    }

    private fun removeObsoleteBaseModel(modelsDir: File, currentModelName: String) {
        try {
            val obsoleteBase = File(modelsDir, "gemma-3-270m-q4_k_m.gguf")
            if (obsoleteBase.exists() && obsoleteBase.name != currentModelName) {
                val deleted = obsoleteBase.delete()
                Log.i("TrailQuest/Storage", "Obsolete base model removed: $deleted (${obsoleteBase.name})")
            }
        } catch (e: Exception) {
            Log.w("TrailQuest/Storage", "Failed to remove obsolete base model", e)
        }
    }

    suspend fun installModel(assetName: String): String = withContext(Dispatchers.IO) {
        val modelsDir = File(context.filesDir, "models")
        if (!modelsDir.exists()) modelsDir.mkdirs()

        cleanStaleTempFiles(modelsDir)

        val modelFile = File(modelsDir, assetName)

        // Use AssetFileDescriptor for reliable asset file size
        val afd = context.assets.openFd("models/$assetName")
        val totalBytes = afd.length
        afd.close()

        // Skip copy only when file exists AND has the correct verified size
        if (modelFile.exists() && modelFile.length() == totalBytes) {
            _progress.value = 1f
            _status.value = "Model ready"
            removeObsoleteBaseModel(modelsDir, assetName)
            return@withContext modelFile.absolutePath
        }

        val tempFile = File(modelsDir, "$assetName.tmp")
        if (tempFile.exists()) tempFile.delete()

        _status.value = "Copying local model..."
        val inputStream = context.assets.open("models/$assetName")
        var copiedBytes = 0L
        var lastEmitMs = 0L

        // ponytail: 2 MB buffer; shrink to 512 KB if low-RAM devices OOM during copy
        val buffer = ByteArray(2 * 1024 * 1024)
        var read: Int

        try {
            inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        copiedBytes += read
                        if (totalBytes > 0) {
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
            if (totalBytes > 0 && tempFile.length() != totalBytes) {
                tempFile.delete()
                throw IOException("Model file size mismatch: copied ${tempFile.length()}, expected $totalBytes")
            }

            // Atomic rename/replace into destination, preserving working model if copy failed
            if (modelFile.exists()) {
                modelFile.delete()
            }
            if (!tempFile.renameTo(modelFile)) {
                tempFile.copyTo(modelFile, overwrite = true)
                tempFile.delete()
            }
        } catch (e: Exception) {
            if (tempFile.exists()) tempFile.delete()
            throw e
        }

        // Migration step: remove obsolete base-model only AFTER new model is verified in place
        removeObsoleteBaseModel(modelsDir, assetName)

        _progress.value = 1f
        _status.value = "AI ready"
        return@withContext modelFile.absolutePath
    }
}
