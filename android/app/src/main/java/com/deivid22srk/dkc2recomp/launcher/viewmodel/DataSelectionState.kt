package com.deivid22srk.dkc2recomp.launcher.viewmodel

/**
 * Fases do fluxo de dados do jogo. Cada fase tem visual, cor, ícone e
 * microanimação próprios na [com.deivid22srk.dkc2recomp.launcher.ui.components.StatusArea].
 *
 * O template NÃO procura nada automaticamente: o app abre direto em [Idle]
 * e só valida quando o usuário escolhe uma pasta (ou ao restaurar silenciosamente
 * uma pasta persistida de sessão anterior).
 */
sealed interface DataPhase {

    /** Estado inicial: nenhum dado selecionado — apenas aguarda o usuário. */
    data object Idle : DataPhase

    /** Validação em andamento (pasta recém-escolhida ou restaurada). */
    data object Validating : DataPhase

    /** Nenhuma pasta válida / nenhum arquivo esperado encontrado. */
    data object NotFound : DataPhase

    /** Pasta validada: [fileName] é o arquivo que deu match no config. */
    data class Found(
        val folderUri: String,
        val fileName: String
    ) : DataPhase

    /** A permissão persistida foi revogada ou a escolha falhou. */
    data object PermissionError : DataPhase
}

/**
 * Estado imutável observado pela UI. Toda a lógica vive no ViewModel —
 * a tela é uma função pura deste estado.
 */
data class DataSelectionUiState(
    val phase: DataPhase = DataPhase.Idle,
)
