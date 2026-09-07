/*
 * Modelo de configurações do port — SOMENTE opções reais do motor.
 *
 * Cada campo espelha uma chave de `launcher.cfg` (veja [LauncherCfg]) que o
 * host nativo consome de verdade no Android (runner/sdl_main.c /
 * runner/android_main.c), com os mesmos limites (ClampInt) e os mesmos
 * defaults de Dkc2LauncherSettingsDefault. O repositório persiste as chaves
 * de motor no filesDir/launcher.cfg — o arquivo que o motor abre a cada
 * partida — e as duas preferências visuais da tela inicial (partículas e
 * reduzir movimento, que são da interface, não do motor) em SharedPreferences.
 *
 * O que NÃO existe aqui e por quê (honestidade > aparência):
 *   - Renderizador/Vulkan: o host Android é SDL2+GLES2 de um caminho só.
 *   - AudioFrequency: o mixer nativo fixa 32040 Hz (kAudioRate).
 *   - Latência de áudio, frame skip, limite de FPS: não há parâmetro lido.
 *   - Opacidade/ocultação do overlay e vibração: o TouchControlsView nativo
 *     não expõe esses parâmetros ainda.
 *   - Escala de resolução, VSync, 21:9, esticado: sem consumo no caminho SDL.
 */
package com.deivid22srk.dkc2recomp.launcher.settings

import android.content.Context

/** Proporção apresentada (runner/dkc2_video.h — Dkc2VideoAspect). */
enum class AspectOption(val label: String, val index: Int, val ratio: Float) {
    NATIVE("4:3", 0, 4f / 3f),
    R16_10("16:10", 1, 16f / 10f),
    R16_9("16:9", 2, 16f / 9f);

    companion object {
        fun fromIndex(index: Int): AspectOption =
            entries.firstOrNull { it.index == index } ?: NATIVE
    }
}

/**
 * Política da faixa não autoral no modo largo (Dkc2VideoEdgePolicy).
 * Só faz sentido quando a proporção não é a nativa 4:3.
 */
enum class EdgeOption(val label: String, val index: Int, val description: String) {
    REFLECT("Espelhar", 0, "A faixa espelha o terreno autoral mais próximo"),
    BARS("Barras", 1, "A faixa fica preta (moldura visível menor)"),
    SHIFT("Deslocar", 2, "A visão é mantida dentro do nível autoral"),
    GLIDE("Deslizar", 3, "Deslocamento liberado gradualmente (padrão)");

    companion object {
        fun fromIndex(index: Int): EdgeOption =
            entries.firstOrNull { it.index == index } ?: GLIDE
    }
}

/** Modelo de vídeo aplicado por LUT de cor no apresentador (desktop_filter.c). */
enum class ScreenFilterOption(val label: String, val index: Int, val description: String) {
    RAW("Bruto", 0, "Sem filtro — cores direto do framebuffer"),
    CRT("CRT", 1, "Cores ajustadas para um tubo (LUT de vídeo)"),
    COMPOSITE("Composite", 2, "Cores ajustadas para sinal composto (LUT)"),
    TRINITRON("Trinitron", 3, "Cores ajustadas para CRT Trinitron (LUT)");

    companion object {
        fun fromIndex(index: Int): ScreenFilterOption =
            entries.firstOrNull { it.index == index } ?: RAW
    }
}

/**
 * Interpolação/upscale do frame (sdl_main.c — Dkc2SdlPresenterSetUpscaler).
 * No Android (sem DKC2_UPSCALER no ambiente) o motor usa Reconstruct apenas
 * quando Upscaler=2; caso contrário vale TextureFilter — por isso o
 * repositório grava os dois de forma consistente.
 */
enum class UpscalerOption(val label: String, val index: Int, val description: String) {
    NEAREST("Nearest", 0, "Pixels nítidos, sem interpolação"),
    BILINEAR("Bilinear", 1, "Interpolação linear suave"),
    RECONSTRUCT("Reconstruct", 2, "Experimental: reconstrói a imagem do dither");

    companion object {
        fun fromIndex(index: Int): UpscalerOption =
            entries.firstOrNull { it.index == index } ?: NEAREST
    }
}

/** Estágios do upscaler Reconstruct (mesmos rótulos do overlay nativo). */
enum class ReconstructModeOption(val label: String, val index: Int) {
    M0("Sharp pixels only", 0),
    M1("+ Dither decoding", 1),
    M2("+ Diagonal edges", 2),
    M3("+ Level-2 slopes", 3),
    M4("+ Level-3 slopes", 4);

    companion object {
        fun fromIndex(index: Int): ReconstructModeOption =
            entries.firstOrNull { it.index == index } ?: M3
    }
}

/**
 * Snapshot imutável das preferências. Defaults = Dkc2LauncherSettingsDefault
 * (nativo): 4:3 nativo, borda Glide, tela Bruta, Nearest, áudio ligado a 100,
 * zona morta 24 e Reconstruct 3/100/50/60.
 */
data class PortSettings(
    // Vídeo (launcher.cfg)
    val aspectIndex: Int = 0,             // AspectIndex    0..2
    val edgePolicy: Int = 3,              // WidescreenEdge 0..3
    val screenKind: Int = 0,              // ScreenKind     0..3
    val upscaler: Int = 0,                // Upscaler       0..2
    val reconstructMode: Int = 3,         // ReconstructMode        0..4
    val reconstructStrength: Int = 100,   // ReconstructStrength    0..100
    val reconstructSoftness: Int = 50,    // ReconstructSoftness    0..100
    val reconstructShading: Int = 60,     // ReconstructShading     0..100
    // Áudio (launcher.cfg)
    val enableAudio: Boolean = true,      // EnableAudio
    val volume: Int = 100,                // Volume         0..100
    // Controles (launcher.cfg) — vale para o gamepad virtual (P1) e para um
    // controle Bluetooth (P1/P2): o motor aplica em deadzone[0] e deadzone[1].
    val deadzone: Int = 24,               // Player1/2Deadzone 0..100
    // Efeitos da tela inicial (SharedPreferences — da interface, não do motor)
    val particlesEnabled: Boolean = true,
    val reduceMotionOverride: Boolean = false,
)

/**
 * Persistência com paridade nativa. As chaves de motor vão para
 * filesDir/launcher.cfg (lido por Dkc2LauncherSettingsLoad a cada boot do
 * SDL_main); as preferências da tela inicial ficam em SharedPreferences com
 * as mesmas chaves das versões anteriores (upgrade preserva a escolha).
 */
class PortSettingsRepository(context: Context) {

    private val appContext = context.applicationContext
    private val prefs =
        appContext.getSharedPreferences("port_screen_prefs", Context.MODE_PRIVATE)

    fun load(): PortSettings {
        val cfg = LauncherCfg.read(appContext)
        fun key(name: String, default: Int): Int = cfg[name] ?: default
        return PortSettings(
            aspectIndex = key(LauncherCfg.KEY_ASPECT, 0).coerceIn(0, 2),
            edgePolicy = key(LauncherCfg.KEY_EDGE, 3).coerceIn(0, 3),
            screenKind = key(LauncherCfg.KEY_SCREEN, 0).coerceIn(0, 3),
            upscaler = key(LauncherCfg.KEY_UPSCALER, 0).coerceIn(0, 2),
            reconstructMode = key(LauncherCfg.KEY_RECONSTRUCT_MODE, 3).coerceIn(0, 4),
            reconstructStrength = key(LauncherCfg.KEY_RECONSTRUCT_STRENGTH, 100).coerceIn(0, 100),
            reconstructSoftness = key(LauncherCfg.KEY_RECONSTRUCT_SOFTNESS, 50).coerceIn(0, 100),
            reconstructShading = key(LauncherCfg.KEY_RECONSTRUCT_SHADING, 60).coerceIn(0, 100),
            enableAudio = key(LauncherCfg.KEY_ENABLE_AUDIO, 1) != 0,
            volume = key(LauncherCfg.KEY_VOLUME, 100).coerceIn(0, 100),
            deadzone = key(LauncherCfg.KEY_DEADZONE_P1, 24).coerceIn(0, 100),
            particlesEnabled = prefs.getBoolean(K_PARTICLES, true),
            reduceMotionOverride = prefs.getBoolean(K_REDUCE_MOTION, false),
        )
    }

    fun save(s: PortSettings) {
        val aspect = s.aspectIndex.coerceIn(0, 2)
        val upscaler = s.upscaler.coerceIn(0, 2)
        // Contrato do motor no Android: sem variável de ambiente, o upscale
        // efetivo é Reconstruct quando Upscaler=2, senão TextureFilter.
        val textureFilter = if (upscaler == UpscalerOption.BILINEAR.index) 1 else 0
        LauncherCfg.mergeAndSave(
            appContext,
            mapOf(
                LauncherCfg.KEY_ASPECT to aspect,
                LauncherCfg.KEY_EDGE to s.edgePolicy.coerceIn(0, 3),
                LauncherCfg.KEY_SCREEN to s.screenKind.coerceIn(0, 3),
                LauncherCfg.KEY_UPSCALER to upscaler,
                LauncherCfg.KEY_TEXTURE_FILTER to textureFilter,
                LauncherCfg.KEY_RECONSTRUCT_MODE to s.reconstructMode.coerceIn(0, 4),
                LauncherCfg.KEY_RECONSTRUCT_STRENGTH to s.reconstructStrength.coerceIn(0, 100),
                LauncherCfg.KEY_RECONSTRUCT_SOFTNESS to s.reconstructSoftness.coerceIn(0, 100),
                LauncherCfg.KEY_RECONSTRUCT_SHADING to s.reconstructShading.coerceIn(0, 100),
                LauncherCfg.KEY_ENABLE_AUDIO to if (s.enableAudio) 1 else 0,
                LauncherCfg.KEY_VOLUME to s.volume.coerceIn(0, 100),
                LauncherCfg.KEY_DEADZONE_P1 to s.deadzone.coerceIn(0, 100),
                LauncherCfg.KEY_DEADZONE_P2 to s.deadzone.coerceIn(0, 100),
            )
        )
        prefs.edit()
            .putBoolean(K_PARTICLES, s.particlesEnabled)
            .putBoolean(K_REDUCE_MOTION, s.reduceMotionOverride)
            .apply()
    }

    private companion object {
        const val K_PARTICLES = "particles_enabled" // mesma chave da v1.0
        const val K_REDUCE_MOTION = "reduce_motion" // mesma chave da v1.0
    }
}
