package com.deivid22srk.dkc2recomp.launcher.ui

import android.net.Uri
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.deivid22srk.dkc2recomp.launcher.config.PortBranding
import com.deivid22srk.dkc2recomp.launcher.settings.PortSettingsViewModel
import com.deivid22srk.dkc2recomp.launcher.ui.background.AmbientParticles
import com.deivid22srk.dkc2recomp.launcher.ui.background.GrainOverlay
import com.deivid22srk.dkc2recomp.launcher.ui.background.ParallaxBackground
import com.deivid22srk.dkc2recomp.launcher.ui.background.rememberParallaxOffset
import com.deivid22srk.dkc2recomp.launcher.ui.components.AnimatedTitle
import com.deivid22srk.dkc2recomp.launcher.ui.components.CreditsButton
import com.deivid22srk.dkc2recomp.launcher.ui.components.CreditsDialog
import com.deivid22srk.dkc2recomp.launcher.ui.components.FolderButton
import com.deivid22srk.dkc2recomp.launcher.ui.components.PrimarySelectButton
import com.deivid22srk.dkc2recomp.launcher.ui.components.SettingsButton
import com.deivid22srk.dkc2recomp.launcher.ui.components.StatusArea
import com.deivid22srk.dkc2recomp.launcher.ui.components.TechStatusChip
import com.deivid22srk.dkc2recomp.launcher.viewmodel.DataPhase
import com.deivid22srk.dkc2recomp.launcher.viewmodel.DataSelectionUiState
import com.deivid22srk.dkc2recomp.launcher.viewmodel.DataSelectionViewModel

/**
 * Roteador da tela: conecta ViewModel, SAF (OpenDocument para o arquivo de
 * ROM + OpenDocumentTree para varredura de pasta), o ViewModel de
 * configurações e o estado de "reduzir movimento" (sistema OU override manual).
 */
@Composable
fun DataSelectionRoute(
    viewModel: DataSelectionViewModel = viewModel(),
    settingsViewModel: PortSettingsViewModel = viewModel(),
    onOpenSettings: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Caminho primário: escolher o ARQUIVO da ROM diretamente.
    // Deliberadamente SEM EXTRA_MIME_TYPES: quando esse extra é definido ele
    // SUBSTITUI o filtro "*/*" e vira uma whitelist exclusiva — extensões de
    // ROM (.sfc/.smc) mapeiam para MIME types dependentes de fabricante e
    // o arquivo aparecia acinzentado em vários aparelhos. Com o "*/*" puro
    // todo arquivo aberto é selecionável; escolhas erradas são rejeitadas
    // pelo gate de SHA-256 do RomStager com feedback claro na tela.
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val name = queryDocumentName(context, uri)
            viewModel.onFilePicked(uri, name)
        }
    }

    // Caminho secundário: escolher uma PASTA — o scanner encontra os dumps
    // (.sfc/.smc/...) e o hash decide qual deles é o baseline suportado.
    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) viewModel.onFolderPicked(uri)
    }

    // Respeita a preferência de acessibilidade do sistema: escalas de
    // animação zeradas = "remover animações" ativo no Android.
    val systemReducedMotion = remember {
        val resolver = context.contentResolver
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f ||
            Settings.Global.getFloat(resolver, Settings.Global.TRANSITION_ANIMATION_SCALE, 1f) == 0f
    }
    val reduceMotion = systemReducedMotion || settings.reduceMotionOverride

    DataSelectionScreen(
        state = state,
        reduceMotion = reduceMotion,
        particlesEnabled = settings.particlesEnabled,
        onSelectData = {
            if (state.phase is DataPhase.Found) viewModel.onStartGame() else filePicker.launch(arrayOf("*/*"))
        },
        onSelectFolder = { folderPicker.launch(null) },
        onOpenSettings = onOpenSettings
    )
}

/** Nome de exibição do documento SAF, com fallback estável. */
private fun queryDocumentName(context: android.content.Context, uri: Uri): String =
    try {
        context.contentResolver.query(
            uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
        )?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        } ?: "rom.sfc"
    } catch (_: Exception) {
        "rom.sfc"
    }

/**
 * Composição da cena AAA:
 *
 *   [fundo parallax em camadas] → [partículas ambiente] → [conteúdo adaptativo]
 *   → [grain de filme por cima de tudo] → [diálogo de créditos]
 *
 * ADAPTATIVO (correção v1.1 — nada cortado com o celular deitado):
 *  - `WindowInsets.safeDrawing` cobre barras + notch lateral em landscape;
 *  - largura ≥ 560 dp (landscape/tablet) → layout em DUAS COLUNAS roláveis:
 *    título + chip técnico à esquerda, status + ações à direita;
 *  - portrait → coluna única centralizada, rolável quando necessário;
 *  - barra inferior (chip + engrenagem) sempre presa ao fundo com insets.
 */
@Composable
fun DataSelectionScreen(
    state: DataSelectionUiState,
    reduceMotion: Boolean,
    particlesEnabled: Boolean,
    onSelectData: () -> Unit,
    onSelectFolder: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val config = PortBranding.config
    var creditsOpen by remember { mutableStateOf(false) }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF07070C))
    ) {
        val compact = maxHeight < 520.dp
        val wide = maxWidth >= 560.dp
        val parallax = rememberParallaxOffset(reduceMotion)

        ParallaxBackground(parallax = parallax, compact = compact)
        AmbientParticles(
            reducedMotion = reduceMotion,
            enabled = particlesEnabled && config.particlesEnabled,
            compact = compact
        )

        if (wide) {
            WideContent(
                state = state,
                reduceMotion = reduceMotion,
                compact = compact,
                onSelectData = onSelectData,
                onSelectFolder = onSelectFolder,
                onOpenCredits = { creditsOpen = true },
                onOpenSettings = onOpenSettings
            )
        } else {
            PortraitContent(
                state = state,
                reduceMotion = reduceMotion,
                compact = compact,
                onSelectData = onSelectData,
                onSelectFolder = onSelectFolder,
                onOpenCredits = { creditsOpen = true },
                onOpenSettings = onOpenSettings
            )
        }

        // Grain de filme acima de TODA a composição (inclusive conteúdo).
        GrainOverlay()

        if (creditsOpen) {
            CreditsDialog(onDismiss = { creditsOpen = false })
        }
    }
}

// ======================================================================
// Portrait: coluna única centralizada (rolável — nunca corta)
// ======================================================================

@Composable
private fun PortraitContent(
    state: DataSelectionUiState,
    reduceMotion: Boolean,
    compact: Boolean,
    onSelectData: () -> Unit,
    onSelectFolder: () -> Unit,
    onOpenCredits: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 24.dp, bottom = 104.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AnimatedTitle(compact = compact, reduceMotion = reduceMotion)

            Spacer(Modifier.height(if (compact) 18.dp else 28.dp))

            StatusArea(state.phase, compact, reduceMotion)

            Spacer(Modifier.height(if (compact) 18.dp else 26.dp))

            PrimarySelectButton(
                phase = state.phase,
                validating = state.phase is DataPhase.Validating,
                compact = compact,
                reduceMotion = reduceMotion,
                onClick = onSelectData,
                modifier = Modifier.widthIn(max = 420.dp)
            )

            FolderButton(
                icon = Icons.Filled.Folder,
                enabled = state.phase !is DataPhase.Found && state.phase !is DataPhase.Validating,
                compact = compact,
                reduceMotion = reduceMotion,
                onClick = onSelectFolder
            )

            Spacer(Modifier.height(if (compact) 10.dp else 16.dp))

            CreditsButton(
                reduceMotion = reduceMotion,
                onClick = onOpenCredits
            )
        }

        BottomBar(
            onOpenSettings = onOpenSettings,
            modifier = Modifier
                .align(Alignment.BottomCenter)
        )
    }
}

// ======================================================================
// Landscape / tablet (largura ≥ 560 dp): duas colunas roláveis
// ======================================================================

@Composable
private fun WideContent(
    state: DataSelectionUiState,
    reduceMotion: Boolean,
    compact: Boolean,
    onSelectData: () -> Unit,
    onSelectFolder: () -> Unit,
    onOpenCredits: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val config = PortBranding.config

    Box(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 36.dp)
                .padding(bottom = 92.dp, top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(32.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ---- Coluna esquerda: identidade ------------------------------
            Column(
                modifier = Modifier
                    .weight(1.15f)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                AnimatedTitle(compact = compact, reduceMotion = reduceMotion)
                Spacer(Modifier.height(20.dp))
                if (config.showTechChip) {
                    TechStatusChip()
                }
                Spacer(Modifier.height(14.dp))
                CreditsButton(
                    reduceMotion = reduceMotion,
                    onClick = onOpenCredits
                )
            }

            // ---- Coluna direita: status + ações ---------------------------
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                StatusArea(state.phase, compact, reduceMotion)

                Spacer(Modifier.height(if (compact) 16.dp else 24.dp))

                PrimarySelectButton(
                    phase = state.phase,
                    validating = state.phase is DataPhase.Validating,
                    compact = compact,
                    reduceMotion = reduceMotion,
                    onClick = onSelectData,
                    modifier = Modifier.widthIn(max = 420.dp)
                )

                FolderButton(
                    icon = Icons.Filled.Folder,
                    enabled = state.phase !is DataPhase.Found && state.phase !is DataPhase.Validating,
                    compact = compact,
                    reduceMotion = reduceMotion,
                    onClick = onSelectFolder
                )
            }
        }

        BottomBar(
            onOpenSettings = onOpenSettings,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

// ======================================================================
// Barra inferior: chip técnico | engrenagem (sempre visível, com insets)
// ======================================================================

@Composable
private fun BottomBar(onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    val config = PortBranding.config

    Row(
        modifier = modifier
            .widthIn(max = 760.dp)
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (config.showTechChip) {
            TechStatusChip()
        } else {
            Spacer(Modifier.size(48.dp))
        }
        SettingsButton(onClick = onOpenSettings)
    }
}
