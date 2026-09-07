package com.deivid22srk.dkc2recomp.launcher.data

import androidx.documentfile.provider.DocumentFile
import com.deivid22srk.dkc2recomp.launcher.config.PortBrandingConfig

/**
 * Busca, dentro da pasta escolhida via SAF, os arquivos de dados que o motor
 * do port pode aceitar — em ordem de preferência.
 *
 * Estratégia em duas passadas (mesma da maioria dos launchers de ports):
 *  1. Nomes exatos — qualquer item de [PortBrandingConfig.expectedDataFiles]
 *     presente na raiz da pasta (comparação case-insensitive) é candidato.
 *  2. Extensões — os arquivos cujo nome termina com uma extensão de
 *     [PortBrandingConfig.acceptableExtensions] são os próximos candidatos
 *     (útil para dumps renomeados).
 *
 * Somente a RAIZ da pasta é inspecionada (rápido e previsível); varredura
 * recursiva fica por conta do port, se necessário.
 *
 * O DKC2 usa [findCandidates] em conjunto com o gate de SHA-256 do
 * [RomStager]: em pastas com várias ROMs, só o dump suportado (DKC2 USA
 * v1.0) valida a seleção — o primeiro candidato que passar no hash vence.
 */
object GameDataScanner {

    /**
     * @return os arquivos candidatos em ordem de preferência (nomes exatos
     *         primeiro, depois extensões). Vazio quando a pasta não contém
     *         nada reconhecível.
     */
    fun findCandidates(folder: DocumentFile, config: PortBrandingConfig): List<DocumentFile> {
        val children = folder.listFiles()
        val candidates = mutableListOf<DocumentFile>()

        // Passada 1: nomes exatos, na ordem declarada no config.
        for (expected in config.expectedDataFiles) {
            children.firstOrNull {
                it.isFile && it.name?.equals(expected, ignoreCase = true) == true
            }?.let { candidates.add(it) }
        }

        // Passada 2: extensões aceitáveis (se configuradas).
        if (config.acceptableExtensions.isNotEmpty()) {
            for (child in children) {
                val name = child.name ?: continue
                if (!child.isFile) continue
                val lower = name.lowercase()
                if (config.acceptableExtensions.any { lower.endsWith(it.lowercase()) }) {
                    candidates.add(child)
                }
            }
        }

        return candidates
    }

    /**
     * @return o nome real do primeiro arquivo detectado (como aparece no
     *         disco), ou null quando a pasta não contém dados reconhecíveis.
     */
    fun findExpectedFile(folder: DocumentFile, config: PortBrandingConfig): String? =
        findCandidates(folder, config).firstOrNull()?.name
}
