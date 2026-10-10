package com.example.trailquest

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.AndroidViewModel
import com.example.trailquest.theme.TrailQuestTheme

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private var _llmProvider: LlmProvider? = null

    fun getOrCreateLlmProvider(): LlmProvider {
        val current = _llmProvider
        if (current == null || (current is LocalGemmaProvider && current.isClosed())) {
            _llmProvider = LocalGemmaProvider(getApplication<Application>())
        }
        return _llmProvider!!
    }

    fun hasInstantiatedProvider(): Boolean = _llmProvider != null

    override fun onCleared() {
        super.onCleared()
        _llmProvider?.cleanup()
        _llmProvider = null
    }
}

class MainActivity : ComponentActivity() {
    private val appViewModel: AppViewModel by viewModels()
    private lateinit var modelInstaller: ModelInstaller

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
                        llmProviderFactory = appViewModel::getOrCreateLlmProvider,
                        modelInstaller = modelInstaller
                    )
                }
            }
        }
    }
}
