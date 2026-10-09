package com.example.trailquest

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.trailquest.ui.benchmark.BenchmarkScreen
import com.example.trailquest.ui.home.HomeScreen
import com.example.trailquest.ui.setup.ExpeditionDraftState
import com.example.trailquest.ui.setup.ExpeditionReviewScreen
import com.example.trailquest.ui.setup.ExpeditionSetupScreen

@Composable
fun MainNavigation(
    llmProviderFactory: () -> LlmProvider,
    modelInstaller: ModelInstaller
) {
    val backStack = rememberNavBackStack(HomeKey)
    val draftState = remember { ExpeditionDraftState() }

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            entry<HomeKey> {
                HomeScreen(
                    onStartExpedition = { backStack.add(ExpeditionSetupKey) },
                    onOpenDeveloperTools = { backStack.add(DeveloperBenchmarkKey) }
                )
            }
            entry<ExpeditionSetupKey> {
                ExpeditionSetupScreen(
                    draftState = draftState,
                    onContinue = { config ->
                        backStack.add(ExpeditionReviewKey(config))
                    },
                    onBack = { backStack.removeLastOrNull() }
                )
            }
            entry<ExpeditionReviewKey> { key ->
                ExpeditionReviewScreen(
                    config = key.config,
                    onEdit = {
                        backStack.removeLastOrNull()
                    },
                    onHome = {
                        backStack.clear()
                        backStack.add(HomeKey)
                    }
                )
            }
            entry<DeveloperBenchmarkKey> {
                BenchmarkScreen(
                    llmProviderFactory = llmProviderFactory,
                    modelInstaller = modelInstaller,
                    onBack = { backStack.removeLastOrNull() }
                )
            }
        }
    )
}
