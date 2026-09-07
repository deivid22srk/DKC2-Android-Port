/*
 * Ponte de configurações entre a interface (Kotlin) e o motor nativo.
 *
 * O host SDL do DKC2Recomp lê `launcher.cfg` do diretório corrente logo no
 * boot (runner/desktop_launcher.c, Dkc2LauncherSettingsLoad), e no Android o
 * corrente é o filesDir do app (runner/android_main.c faz chdir para
 * SDL_AndroidGetInternalStoragePath()). Escrever o arquivo neste diretório é
 * portanto o contrato oficial: nenhuma alteração nativa é necessária e cada
 * partida nasce com as preferências escolhidas aqui.
 *
 * PARIDADE COM O PARSER C (importante — não "melhorar" sem ajustar os dois):
 *   sscanf(line, "%47[^=]=%d", key, &value) != 2  → linha ignorada.
 *   - A chave NÃO é aparada: "Volume = 50" leria a chave "Volume " (com
 *     espaço) e seria descartada. Este gravador escreve "Chave=Valor" sem
 *     espaços, exatamente como Dkc2LauncherSettingsSave.
 *   - Chaves/valores fora do formato são preservados como estão na memória
 *     (mapa bruto) e regravados intactos — chaves de bind (Player1Key0 etc.)
 *     escritas por outros hosts sobrevivem a uma edição feita aqui.
 *   - Faixas válidas por chave ficam no lado nativo (ClampInt); o repositório
 *     replica os mesmos limites para que a UI nunca escreva valor rejeitado.
 *
 * Chaves gerenciadas aqui: apenas as que o caminho Android realmente consome
 * (runner/sdl_main.c). Campos como AudioFrequency (o mixer fixa 32040 Hz),
 * WindowScale, Renderer e PlayerNSource (android_main.c força gamepad na P1)
 * NÃO são expostos — uma opção que o motor ignora é uma opção falsa.
 */
package com.deivid22srk.dkc2recomp.launcher.settings

import android.content.Context
import android.util.Log
import java.io.File

object LauncherCfg {

    private const val TAG = "LauncherCfg"
    private const val FILE_NAME = "launcher.cfg"
    private const val TMP_SUFFIX = ".tmp"
    private const val MAX_KEY_LENGTH = 47 // sscanf "%47[^=]"

    // ---- Chaves gerenciadas (mesmos nomes do parser C) ---------------------
    const val KEY_ASPECT = "AspectIndex"             // 0 nativo 4:3, 1 16:10, 2 16:9
    const val KEY_EDGE = "WidescreenEdge"            // 0 reflect, 1 bars, 2 shift, 3 glide
    const val KEY_SCREEN = "ScreenKind"              // 0 raw, 1 crt, 2 composite, 3 trinitron
    const val KEY_UPSCALER = "Upscaler"              // 0 nearest, 1 bilinear, 2 reconstruct
    const val KEY_TEXTURE_FILTER = "TextureFilter"   // 0 nearest, 1 bilinear
    const val KEY_RECONSTRUCT_MODE = "ReconstructMode"       // 0..4
    const val KEY_RECONSTRUCT_STRENGTH = "ReconstructStrength" // 0..100
    const val KEY_RECONSTRUCT_SOFTNESS = "ReconstructSoftness" // 0..100
    const val KEY_RECONSTRUCT_SHADING = "ReconstructShading"   // 0..100
    const val KEY_ENABLE_AUDIO = "EnableAudio"       // 0/1
    const val KEY_VOLUME = "Volume"                  // 0..100
    const val KEY_DEADZONE_P1 = "Player1Deadzone"    // 0..100
    const val KEY_DEADZONE_P2 = "Player2Deadzone"    // 0..100

    /** Arquivo exato que o motor abre com fopen("launcher.cfg") após o chdir. */
    fun file(context: Context): File = File(context.filesDir, FILE_NAME)

    /**
     * Lê o arquivo para um mapa de inserção ordenada, espelhando o sscanf do
     * parser C (chave sem aparar, valor inteiro com espaços iniciais ok).
     * Linhas que o parser C rejeitaria não entram no mapa — mas, como só
     * regravamos linhas que sabemos representar, linhas estranhas (sem "=",
     * comentários, binds muito longos) são descartadas silenciosamente, o que
     * é inofensivo: o motor também as ignoraria.
     */
    fun read(context: Context): LinkedHashMap<String, Int> {
        val map = LinkedHashMap<String, Int>()
        val target = file(context)
        if (!target.isFile) return map
        try {
            target.useLines { lines ->
                for (raw in lines) {
                    val line = raw.trimEnd('\n', '\r')
                    val eq = line.indexOf('=')
                    if (eq <= 0 || eq > MAX_KEY_LENGTH) continue
                    val key = line.substring(0, eq)
                    val value = line.substring(eq + 1).trimStart().toIntOrNull() ?: continue
                    map[key] = value
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao ler launcher.cfg; usando defaults do motor", e)
            return LinkedHashMap()
        }
        return map
    }

    /**
     * Mescla [updates] sobre o conteúdo atual e regrava o arquivo. Valores
     * fora de faixa NÃO são recusados (o motor aplica ClampInt ao carregar),
     * mas o repositório já grava dentro das faixas nativas.
     */
    fun mergeAndSave(context: Context, updates: Map<String, Int>): Boolean {
        val merged = read(context)
        merged.putAll(updates)
        return write(context, merged)
    }

    /** Gravação atômica (tmp → rename), mesmo padrão do RomStager da ROM. */
    fun write(context: Context, entries: Map<String, Int>): Boolean {
        val target = file(context)
        val tmp = File(context.filesDir, FILE_NAME + TMP_SUFFIX)
        return try {
            tmp.outputStream().bufferedWriter().use { writer ->
                for ((key, value) in entries) {
                    if (key.isEmpty() || key.length > MAX_KEY_LENGTH || key.contains('=')) continue
                    writer.write(key)
                    writer.write("=")
                    writer.write(value.toString())
                    writer.write("\n")
                }
            }
            if (target.exists()) target.delete()
            if (!tmp.renameTo(target)) {
                // Rename entre entradas pode falhar em alguns FS; cópia robusta.
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao gravar launcher.cfg", e)
            tmp.delete()
            false
        }
    }
}
