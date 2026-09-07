/*
 * Tela de Configurações dedicada.
 *
 * Visual coerente com a cena principal (mesmo fundo em camadas, partículas e
 * grain), seções em painéis de vidro e controles com alvos de 48 dp. Tudo é
 * persistido por [PortSettingsViewModel] — e CADA opção desta tela é uma
 * preferência que o motor nativo de fato consome (chaves de launcher.cfg; ver
 * [com.deivid22srk.dkc2recomp.launcher.settings.LauncherCfg]). Nada de
 * controles decorativos: se está aqui, funciona.
 */
package com.deivid22srk.dkc2recomp.launcher.ui.settings

import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Texture
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deivid22srk.dkc2recomp.launcher.config.PortBranding
import com.deivid22srk.dkc2recomp.launcher.settings.AspectOption
import com.deivid22srk.dkc2recomp.launcher.settings.EdgeOption
import com.deivid22srk.dkc2recomp.launcher.settings.PortSettings
import com.deivid22srk.dkc2recomp.launcher.settings.PortSettingsViewModel
import com.deivid22srk.dkc2recomp.launcher.settings.ReconstructModeOption
import com.deivid22srk.dkc2recomp.launcher.settings.ScreenFilterOption
import com.deivid22srk.dkc2recomp.launcher.settings.UpscalerOption
import com.deivid22srk.dkc2recomp.launcher.ui.background.AmbientParticles
import com.deivid22srk.dkc2recomp.launcher.ui.background.GrainOverlay
import com.deivid22srk.dkc2recomp.launcher.ui.background.ParallaxBackground
import com.deivid22srk.dkc2recomp.launcher.viewmodel.DataPhase
import com.deivid22srk.dkc2recomp.launcher.viewmodel.DataSelectionUiState
import com.deivid22srk.dkc2recomp.launcher.viewmodel.DataSelectionViewModel
import kotlin.math.roundToInt

/**
 * Rota da tela de Configurações: conecta os dois ViewModels (preferências +
 * seleção de dados) e o estado de "reduzir movimento" do sistema.
 */
@Composable
fun SettingsRoute(
    settingsViewModel: PortSettingsViewModel,
    selectionViewModel: DataSelectionViewModel,
    onBack: () -> Unit,
) {
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
    val selectionState by selectionViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val systemReducedMotion = remember {
        val resolver = context.contentResolver
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f ||
            Settings.Global.getFloat(resolver, Settings.Global.TRANSITION_ANIMATION_SCALE, 1f) == 0f
    }

    SettingsScreen(
        settings = settings,
        selectionState = selectionState,
        systemReducedMotion = systemReducedMotion,
        onSettingsChange = settingsViewModel::update,
        onClearSelection = selectionViewModel::clearSavedSelection,
        onBack = onBack
    )
}

@Composable
fun SettingsScreen(
    settings: PortSettings,
    selectionState: DataSelectionUiState,
    systemReducedMotion: Boolean,
    onSettingsChange: ((PortSettings) -> PortSettings) -> Unit,
    onClearSelection: () -> Unit,
    onBack: () -> Unit,
) {
    val config = PortBranding.config
    val accent = config.accent
    val reduceMotion = systemReducedMotion || settings.reduceMotionOverride
    val aspect = AspectOption.fromIndex(settings.aspectIndex)
    val upscaler = UpscalerOption.fromIndex(settings.upscaler)
    val edge = EdgeOption.fromIndex(settings.edgePolicy)
    val screenFilter = ScreenFilterOption.fromIndex(settings.screenKind)

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF07070C))
    ) {
        val compact = maxHeight < 560.dp

        ParallaxBackground(parallax = Offset.Zero, compact = compact)
        AmbientParticles(
            reducedMotion = reduceMotion,
            enabled = settings.particlesEnabled && config.particlesEnabled,
            compact = compact
        )

        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            // ---- Cabeçalho -------------------------------------------------
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = config.contentDescBack,
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        text = config.labelSettingsTitle,
                        color = Color.White,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = config.labelSettingsSubtitle,
                        color = Color.White.copy(alpha = 0.50f),
                        fontSize = 11.sp,
                        lineHeight = 14.sp
                    )
                }
            }

            // ---- Conteúdo rolável ------------------------------------------
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(Modifier.height(10.dp))

                // ============================ VÍDEO ==========================
                SettingsSection(title = "Vídeo", icon = Icons.Filled.Tune, accent = accent) {
                    SettingLabel("Proporção")
                    Spacer(Modifier.height(8.dp))
                    AspectRatioPreview(ratio = aspect.ratio, label = aspect.label, accent = accent)
                    Spacer(Modifier.height(12.dp))
                    ChoiceChipsRow(
                        options = AspectOption.entries.toList(),
                        selected = aspect,
                        accent = accent
                    ) { option ->
                        onSettingsChange { it.copy(aspectIndex = option.index) }
                    }
                    SettingHint(
                        "16:10 e 16:9 ampliam o quadro com a política de borda abaixo; " +
                            "o jogo autoral permanece 4:3."
                    )

                    if (aspect != AspectOption.NATIVE) {
                        Spacer(Modifier.height(16.dp))
                        SettingLabel("Borda no modo largo")
                        Spacer(Modifier.height(10.dp))
                        ChoiceChipsRow(
                            options = EdgeOption.entries.toList(),
                            selected = edge,
                            accent = accent
                        ) { option ->
                            onSettingsChange { it.copy(edgePolicy = option.index) }
                        }
                        SettingHint(edge.description)
                    }

                    Spacer(Modifier.height(16.dp))
                    SettingLabel("Modelo de vídeo")
                    Spacer(Modifier.height(10.dp))
                    ChoiceChipsRow(
                        options = ScreenFilterOption.entries.toList(),
                        selected = screenFilter,
                        accent = accent
                    ) { option ->
                        onSettingsChange { it.copy(screenKind = option.index) }
                    }
                    SettingHint(screenFilter.description)

                    Spacer(Modifier.height(16.dp))
                    SettingLabel("Upscale")
                    Spacer(Modifier.height(10.dp))
                    ChoiceChipsRow(
                        options = UpscalerOption.entries.toList(),
                        selected = upscaler,
                        accent = accent
                    ) { option ->
                        onSettingsChange { it.copy(upscaler = option.index) }
                    }
                    SettingHint(upscaler.description)
                }

                Spacer(Modifier.height(14.dp))

                // ================== RECONSTRUCT (EXPERIMENTO) ================
                if (upscaler == UpscalerOption.RECONSTRUCT) {
                    SettingsSection(
                        title = "Reconstruct — experimento",
                        icon = Icons.Filled.Texture,
                        accent = accent
                    ) {
                        SettingLabel("Estágios de reconstrução")
                        Spacer(Modifier.height(10.dp))
                        ChoiceChipsRow(
                            options = ReconstructModeOption.entries.toList(),
                            selected = ReconstructModeOption.fromIndex(settings.reconstructMode),
                            accent = accent
                        ) { option ->
                            onSettingsChange { it.copy(reconstructMode = option.index) }
                        }
                        SettingHint(
                            "Cada estágio decodifica mais informação do dither do SNES " +
                                "(rótulos idênticos aos do motor)."
                        )

                        Spacer(Modifier.height(16.dp))
                        SliderRow(
                            label = "Força das bordas",
                            valueText = "${settings.reconstructStrength}%",
                            value = settings.reconstructStrength.toFloat(),
                            valueRange = 0f..100f,
                            steps = 19,
                            accent = accent
                        ) { value ->
                            onSettingsChange { it.copy(reconstructStrength = value.roundToInt()) }
                        }

                        Spacer(Modifier.height(12.dp))
                        SliderRow(
                            label = "Suavidade",
                            valueText = "${settings.reconstructSoftness}%",
                            value = settings.reconstructSoftness.toFloat(),
                            valueRange = 0f..100f,
                            steps = 19,
                            accent = accent
                        ) { value ->
                            onSettingsChange { it.copy(reconstructSoftness = value.roundToInt()) }
                        }

                        Spacer(Modifier.height(12.dp))
                        SliderRow(
                            label = "Sombreamento",
                            valueText = "${settings.reconstructShading}%",
                            value = settings.reconstructShading.toFloat(),
                            valueRange = 0f..100f,
                            steps = 19,
                            accent = accent
                        ) { value ->
                            onSettingsChange { it.copy(reconstructShading = value.roundToInt()) }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                }

                // =========================== ÁUDIO ===========================
                SettingsSection(title = "Áudio", icon = Icons.Filled.VolumeUp, accent = accent) {
                    ToggleRow(
                        label = "Ativar áudio",
                        subtitle = "Liga ou desliga toda a saída de som do motor",
                        checked = settings.enableAudio,
                        accent = accent
                    ) { checked ->
                        onSettingsChange { it.copy(enableAudio = checked) }
                    }

                    Spacer(Modifier.height(12.dp))
                    SliderRow(
                        label = "Volume",
                        valueText = "${settings.volume}%",
                        value = settings.volume.toFloat(),
                        valueRange = 0f..100f,
                        steps = 19,
                        enabled = settings.enableAudio,
                        accent = accent
                    ) { value ->
                        onSettingsChange { it.copy(volume = value.roundToInt()) }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // ========================= CONTROLES =========================
                SettingsSection(title = "Controles", icon = Icons.Filled.Gamepad, accent = accent) {
                    SliderRow(
                        label = "Zona morta dos analógicos",
                        subtitle = "Vale para o gamepad virtual (jogador 1) e para um " +
                            "controle Bluetooth (jogadores 1 e 2)",
                        valueText = "${settings.deadzone}%",
                        value = settings.deadzone.toFloat(),
                        valueRange = 0f..100f,
                        steps = 19,
                        accent = accent
                    ) { value ->
                        onSettingsChange { it.copy(deadzone = value.roundToInt()) }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // =========================== EFEITOS =========================
                SettingsSection(title = "Efeitos da tela inicial", icon = Icons.Filled.AutoAwesome, accent = accent) {
                    ToggleRow(
                        label = config.labelToggleParticles,
                        subtitle = "Partículas ambiente da cena principal",
                        checked = settings.particlesEnabled,
                        accent = accent
                    ) { checked ->
                        onSettingsChange { it.copy(particlesEnabled = checked) }
                    }

                    Spacer(Modifier.height(12.dp))
                    ToggleRow(
                        label = config.labelToggleMotion,
                        subtitle = "Desativa parallax, pulso e entradas animadas",
                        checked = settings.reduceMotionOverride,
                        accent = accent
                    ) { checked ->
                        onSettingsChange { it.copy(reduceMotionOverride = checked) }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // ======================== DADOS DO JOGO ======================
                SettingsSection(title = "Dados do jogo", icon = Icons.Filled.FolderOpen, accent = accent) {
                    SelectionSummary(selectionState, accent)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Arquivos aceitos: dumps .sfc/.smc/.fig/.swc — o motor " +
                            "só inicia com o DKC2 USA v1.0 exato (verificação de SHA-256).",
                        color = Color.White.copy(alpha = 0.42f),
                        fontSize = 10.5.sp,
                        lineHeight = 14.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(Modifier.height(14.dp))
                    DangerButton(
                        label = config.labelClearSelection,
                        enabled = selectionState.phase !is DataPhase.Idle,
                        onClick = onClearSelection
                    )
                }

                Spacer(Modifier.height(16.dp))
                Text(
                    text = config.labelSettingsFooter,
                    color = Color.White.copy(alpha = 0.40f),
                    fontSize = 10.5.sp,
                    lineHeight = 15.sp
                )
                Spacer(Modifier.height(32.dp))
            }
        }

        GrainOverlay()
    }
}

// ======================================================================
// Componentes internos da tela
// ======================================================================

@Composable
private fun SettingsSection(
    title: String,
    icon: ImageVector,
    accent: Color,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.045f))
            .border(1.dp, Color.White.copy(alpha = 0.09f), RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 18.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(accent.copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(17.dp))
            }
            Text(
                text = title,
                color = Color.White,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp
            )
        }
        Spacer(Modifier.height(16.dp))
        content()
    }
}

@Composable
private fun SettingLabel(text: String) {
    Text(
        text = text.uppercase(),
        color = Color.White.copy(alpha = 0.55f),
        fontSize = 10.5.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.4.sp
    )
}

/** Linha de apoio explicativa logo abaixo de um controle. */
@Composable
private fun SettingHint(text: String) {
    Spacer(Modifier.height(6.dp))
    Text(
        text = text,
        color = Color.White.copy(alpha = 0.45f),
        fontSize = 11.sp,
        lineHeight = 14.sp
    )
}

/** Linha de chips selecionáveis com scroll horizontal (nunca corta). */
@Composable
private fun <T> ChoiceChipsRow(
    options: List<T>,
    selected: T,
    accent: Color,
    label: (T) -> String = { it.toString() },
    onSelect: (T) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Box(
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (isSelected) accent.copy(alpha = 0.95f)
                        else Color.White.copy(alpha = 0.07f)
                    )
                    .border(
                        1.dp,
                        if (isSelected) Color.Transparent else Color.White.copy(alpha = 0.14f),
                        RoundedCornerShape(14.dp)
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onSelect(option) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label(option),
                    color = if (isSelected) Color(0xFF12100B) else Color.White.copy(alpha = 0.72f),
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.4.sp
                )
            }
        }
    }
}

@Composable
private fun ToggleRow(
    label: String,
    subtitle: String?,
    checked: Boolean,
    accent: Color,
    onChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                color = Color.White.copy(alpha = 0.82f),
                fontSize = 13.5.sp
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 11.sp,
                    lineHeight = 14.sp
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = accent,
                checkedThumbColor = Color(0xFF0B0B12)
            )
        )
    }
}

@Composable
private fun SliderRow(
    label: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    accent: Color,
    enabled: Boolean = true,
    subtitle: String? = null,
    onChange: (Float) -> Unit,
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = label,
                    color = Color.White.copy(alpha = if (enabled) 0.82f else 0.40f),
                    fontSize = 13.5.sp
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        color = Color.White.copy(alpha = 0.45f),
                        fontSize = 11.sp,
                        lineHeight = 14.sp
                    )
                }
            }
            Text(
                text = valueText,
                color = accent.copy(alpha = if (enabled) 1f else 0.4f),
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
        Slider(
            value = value,
            onValueChange = { if (enabled) onChange(it) },
            valueRange = valueRange,
            steps = steps,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = accent,
                activeTrackColor = accent,
                inactiveTrackColor = Color.White.copy(alpha = 0.14f)
            )
        )
    }
}

/** Pré-visualização do frame do jogo na proporção selecionada. */
@Composable
private fun AspectRatioPreview(ratio: Float, label: String, accent: Color) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(88.dp)
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val inset = 6.dp.toPx()
            val availW = size.width - inset * 2
            val availH = size.height - inset * 2
            var w = availW
            var h = w / ratio
            if (h > availH) {
                h = availH
                w = h * ratio
            }
            val frameW = w
            val frameH = h
            val left = (size.width - frameW) / 2f
            val top = (size.height - frameH) / 2f
            val corner = CornerRadius(10.dp.toPx(), 10.dp.toPx())

            // Moldura preenchida + contorno accent + guias de terços.
            drawRoundRect(
                color = Color.Black.copy(alpha = 0.55f),
                topLeft = Offset(left, top),
                size = Size(frameW, frameH),
                cornerRadius = corner
            )
            drawRoundRect(
                color = accent.copy(alpha = 0.95f),
                topLeft = Offset(left, top),
                size = Size(frameW, frameH),
                cornerRadius = corner,
                style = Stroke(width = 1.6.dp.toPx())
            )
            drawLine(
                color = accent.copy(alpha = 0.30f),
                start = Offset(left + frameW / 2f, top),
                end = Offset(left + frameW / 2f, top + frameH),
                strokeWidth = 1.dp.toPx()
            )
            drawLine(
                color = accent.copy(alpha = 0.30f),
                start = Offset(left, top + frameH / 2f),
                end = Offset(left + frameW, top + frameH / 2f),
                strokeWidth = 1.dp.toPx()
            )
        }
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.65f),
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.2.sp,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

/** Resumo do estado atual da seleção de dados (ligado ao ViewModel real). */
@Composable
private fun SelectionSummary(state: DataSelectionUiState, accent: Color) {
    val (label, color) = when (val phase = state.phase) {
        is DataPhase.Found -> "ROM verificada · ${phase.fileName}" to Color(0xFF4ADE80)
        is DataPhase.Validating -> "Validando…" to accent
        is DataPhase.NotFound -> "A seleção salva não contém a ROM suportada." to Color(0xFFFF6B6B)
        is DataPhase.PermissionError -> "Permissão de leitura revogada." to Color(0xFFFF6B6B)
        is DataPhase.Idle -> "Nenhuma ROM salva." to Color.White.copy(alpha = 0.55f)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            Modifier
                .size(8.dp)
                .background(color, CircleShape)
        )
        Text(
            text = label,
            color = color,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun DangerButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val red = Color(0xFFFF6B6B)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(red.copy(alpha = if (enabled) 0.10f else 0.05f))
            .border(
                1.dp,
                red.copy(alpha = if (enabled) 0.35f else 0.15f),
                RoundedCornerShape(14.dp)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled
            ) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
            .padding(horizontal = 16.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.DeleteOutline,
                contentDescription = null,
                tint = red.copy(alpha = if (enabled) 1f else 0.4f),
                modifier = Modifier.size(17.dp)
            )
            Text(
                text = label,
                color = red.copy(alpha = if (enabled) 1f else 0.4f),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
