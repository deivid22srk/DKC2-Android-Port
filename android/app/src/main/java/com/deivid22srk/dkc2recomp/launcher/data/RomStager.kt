package com.deivid22srk.dkc2recomp.launcher.data

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/**
 * Cópia e verificação da ROM no armazenamento interno do app.
 *
 * Espelha EXATAMENTE o contrato do runtime nativo (`runner/verified_rom.c`),
 * para que a tela inicial nunca entregue ao motor um arquivo que seria
 * rejeitado lá:
 *
 *   - cópia em streaming para um arquivo temporário (sem carregar 4 MiB
 *     na memória duas vezes);
 *   - cabeçalho de copiadora: se o tamanho % 1024 == 512, os primeiros
 *     512 bytes são ignorados no hash (mesma regra do nativo);
 *   - payload precisa ter exatamente 0x400000 bytes (4 MiB sem header);
 *   - SHA-256 do payload precisa bater com o baseline DKC2 USA v1.0.
 *
 * A troca do arquivo final é atômica na prática: só renomeamos
 * `rom.sfc.tmp` → `rom.sfc` depois de tudo verificado, então a ROM antiga
 * permanece intacta se a validação falhar no meio do caminho.
 */
object RomStager {

    /** Tamanho exato do payload suportado (4 MiB sem cabeçalho). */
    const val PAYLOAD_BYTES: Long = 0x400000L

    /** SHA-256 do baseline suportado: DKC2 USA v1.0, 4 MiB headerless. */
    val SUPPORTED_SHA256: ByteArray = byteArrayOf(
        0x35.toByte(), 0x42.toByte(), 0x1a.toByte(), 0x9a.toByte(),
        0xf9.toByte(), 0xdd.toByte(), 0x01.toByte(), 0x1b.toByte(),
        0x40.toByte(), 0xb9.toByte(), 0x1f.toByte(), 0x79.toByte(),
        0x21.toByte(), 0x92.toByte(), 0xaf.toByte(), 0x9f.toByte(),
        0x99.toByte(), 0xc9.toByte(), 0x32.toByte(), 0x01.toByte(),
        0xd8.toByte(), 0xd3.toByte(), 0x94.toByte(), 0x02.toByte(),
        0x6b.toByte(), 0xdf.toByte(), 0xb4.toByte(), 0x2c.toByte(),
        0xbf.toByte(), 0x2d.toByte(), 0x86.toByte(), 0x33.toByte(),
    )

    sealed class StageResult {
        /** ROM copiada, verificada e staged como `rom.sfc`. */
        data class Staged(val displayName: String) : StageResult()

        /** Arquivo ilegível, tamanho impossível ou hash fora do baseline. */
        data object Unsupported : StageResult()
    }

    /** Arquivo staged que o motor nativo consome (`filesDir/rom.sfc`). */
    fun stagedRom(context: Context): File = File(context.filesDir, "rom.sfc")

    private fun tempFile(context: Context): File = File(context.filesDir, "rom.sfc.tmp")

    /**
     * Copia [uri] para o staging, valida tamanho + SHA-256 e, só depois de
     * aprovada, substitui a ROM staged anterior. Retorna [StageResult].
     */
    fun verifyAndStage(context: Context, uri: Uri, displayName: String): StageResult {
        val staging = tempFile(context)
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                staging.outputStream().use { output -> input.copyTo(output, 64 * 1024) }
            } ?: return cleanup(staging)
            if (!isSupportedFile(staging)) return cleanup(staging)
            val target = stagedRom(context)
            if (target.exists() && !target.delete()) return cleanup(staging)
            if (!staging.renameTo(target)) return cleanup(staging)
            StageResult.Staged(displayName)
        } catch (_: Exception) {
            cleanup(staging)
        }
    }

    /**
     * Verifica a ROM já staged (hash em streaming, ~4 MiB). Usada no boot
     * para restaurar o estado "pronto" sem depender de permissões SAF.
     */
    fun isStagedRomValid(context: Context): Boolean {
        val rom = stagedRom(context)
        return rom.isFile && isSupportedFile(rom)
    }

    /** Remove a ROM staged e resíduos (`.rejected`, temporário). */
    fun clearStaged(context: Context) {
        stagedRom(context).delete()
        File(context.filesDir, "rom.sfc.rejected").delete()
        tempFile(context).delete()
    }

    /** Mesma regra do verified_rom.c: skip de header, 4 MiB, SHA-256 fixo. */
    private fun isSupportedFile(file: File): Boolean {
        val length = file.length()
        if (length <= 0L) return false
        val skip = if (length % 1024L == 512L) 512L else 0L
        val payload = length - skip
        if (payload != PAYLOAD_BYTES) return false
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream ->
            if (skip > 0 && skipFully(stream, skip) != skip) return false
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                if (read > 0) digest.update(buffer, 0, read)
            }
        }
        return digest.digest().contentEquals(SUPPORTED_SHA256)
    }

    private fun skipFully(stream: InputStream, count: Long): Long {
        var remaining = count
        val buffer = ByteArray(64 * 1024)
        while (remaining > 0) {
            val read = stream.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (read < 0) break
            remaining -= read
        }
        return count - remaining
    }

    private fun cleanup(staging: File): StageResult {
        staging.delete()
        return StageResult.Unsupported
    }
}
