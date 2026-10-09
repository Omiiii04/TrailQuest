package com.example.trailquest

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.trailquest.theme.TrailQuestTheme

class MainActivity : ComponentActivity() {
    private var _llmProvider: LlmProvider? = null
    private lateinit var modelInstaller: ModelInstaller

    private fun getOrCreateLlmProvider(): LlmProvider {
        if (_llmProvider == null) {
            _llmProvider = LocalGemmaProvider(applicationContext)
        }
        return _llmProvider!!
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        modelInstaller = ModelInstaller(applicationContext)

        setContent {
            TrailQuestTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainNavigation(
                        llmProviderFactory = ::getOrCreateLlmProvider,
                        modelInstaller = modelInstaller
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _llmProvider?.cleanup()
    }
}
