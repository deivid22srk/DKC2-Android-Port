package com.deivid22srk.dkc2recomp.launcher

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.deivid22srk.dkc2recomp.launcher.ui.DataSelectionRoute
import com.deivid22srk.dkc2recomp.launcher.ui.settings.SettingsRoute
import com.deivid22srk.dkc2recomp.launcher.ui.theme.PortScreenTheme
import com.deivid22srk.dkc2recomp.launcher.viewmodel.DataSelectionViewModel
import com.deivid22srk.dkc2recomp.launcher.settings.PortSettingsViewModel

/**
 * Tela inicial do port DKC2Recomp — front-end visual adaptado do template
 * "Port Screen Template" (Kotlin + Jetpack Compose). Toda a interface é
 * edge-to-edge em MODO IMERSIVO: barras de sistema ocultas e re-ocultadas
 * sempre que o foco volta (diálogos, swipe temporário etc.).
 *
 * Fluxo de dados:
 *   1. o usuário seleciona a ROM (arquivo via ACTION_OPEN_DOCUMENT, ou uma
 *      pasta varrida pelo scanner) na [DataSelectionRoute];
 *   2. o [DataSelectionViewModel] copia e valida o arquivo com o MESMO gate
 *      do runtime nativo (DKC2 USA v1.0 — ver RomStager);
 *   3. ao tocar em "Iniciar Jogo", o callback abaixo entrega os dados e
 *      inicia [com.deivid22srk.dkc2recomp.MainActivity] (host SDL), que
 *      consome `filesDir/rom.sfc` já staged — sem cópia duplicada, sem
 *      espera no lado nativo.
 *
 * A [com.deivid22srk.dkc2recomp.MainActivity] (SDL) mantém seu próprio
 * picker como fallback de robustez, mas no fluxo normal ele nunca é usado.
 */
class LauncherActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Mantém a tela ligada durante a navegação (comportamento de um port).
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            PortScreenTheme {
                // ViewModels no escopo da Activity: compartilhados entre as
                // telas de Seleção e Configurações via navegação.
                val selectionViewModel: DataSelectionViewModel = viewModel()
                val settingsViewModel: PortSettingsViewModel = viewModel()

                // ==========================================================
                // PONTO DE INTEGRAÇÃO DO MOTOR DO PORT
                //
                // Chega aqui somente com a ROM staged e verificada
                // (SHA-256 do baseline). O host SDL lê `rom.sfc` do
                // armazenamento interno e revalida nativamente — defesa
                // em profundidade, zero custo extra para o usuário.
                // ==========================================================
                selectionViewModel.onLaunchGame = { _, _ ->
                    startActivity(
                        Intent(this, com.deivid22srk.dkc2recomp.MainActivity::class.java)
                    )
                }

                AppNavigation(selectionViewModel, settingsViewModel)
            }
        }

        hideSystemBars()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}

private object Routes {
    const val SELECTION = "selection"
    const val SETTINGS = "settings"
}

@Composable
private fun AppNavigation(
    selectionViewModel: DataSelectionViewModel,
    settingsViewModel: PortSettingsViewModel,
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.SELECTION,
        enterTransition = { fadeIn(tween(320)) },
        exitTransition = { fadeOut(tween(220)) },
        popEnterTransition = { fadeIn(tween(320)) },
        popExitTransition = { fadeOut(tween(220)) }
    ) {
        composable(Routes.SELECTION) {
            DataSelectionRoute(
                viewModel = selectionViewModel,
                settingsViewModel = settingsViewModel,
                onOpenSettings = {
                    navController.navigate(Routes.SETTINGS) { launchSingleTop = true }
                }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsRoute(
                settingsViewModel = settingsViewModel,
                selectionViewModel = selectionViewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
