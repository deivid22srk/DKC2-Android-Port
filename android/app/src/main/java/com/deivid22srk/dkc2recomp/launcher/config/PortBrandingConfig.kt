/*
 * ============================================================================
 *  PORT BRANDING CONFIG
 *  O ÚNICO ARQUIVO QUE UM NOVO PORT PRECISA EDITAR.
 * ============================================================================
 *
 *  Este template é 100% genérico: nenhuma tela, cor, texto, partícula ou
 *  referência de arte está fixada no código da interface. Tudo o que dá
 *  identidade a um port — nome, paleta, arte de fundo, arquivos de dados
 *  esperados, partículas ambiente, textos, links de créditos — vem daqui.
 *
 *  COMO ADAPTAR PARA UM NOVO PORT (resumo; guia completo no README.md):
 *
 *    1. Edite os valores do objeto [PortBranding] lá embaixo.
 *    2. Troque  app/src/main/res/drawable-nodpi/bg_cinematic.jpg  pela arte
 *       de fundo do seu port (mesmo nome de arquivo) — ou defina
 *       `backgroundArtRes = null` para um fundo procedural sem imagem.
 *    3. Troque  res/drawable/ic_logo_mark.xml  pelo logotipo do port (SVG
 *       convertido para VectorDrawable) — ou desative com `showLogo = false`.
 *    4. Ajuste [PortBrandingConfig.expectedDataFiles] para os arquivos que
 *       o seu motor procura (ex.: "default.xex", "game.iso", "data.pak").
 *    5. Ajuste [PortBrandingConfig.links] com os links do portador
 *       (YouTube, GitHub, Telegram e repositório do projeto base).
 *    6. Conecte a inicialização do seu motor em
 *       [com.deivid22srk.dkc2recomp.launcher.viewmodel.DataSelectionViewModel.onStartGame].
 *
 *  NADA MAIS precisa ser tocado. Layout, animações, acessibilidade e fluxo
 *  SAF permanecem intactos entre ports.
 * ============================================================================
 */
package com.deivid22srk.dkc2recomp.launcher.config

import androidx.compose.ui.graphics.Color

/**
 * Tipos de partícula ambiente parametrizáveis. Escolha o clima do seu port:
 * - [DUST]   : poeira cinematográfica cinza, flutuação lenta e discreta.
 * - [EMBERS] : brasas quentes subindo (combina com paleta âmbar/fogo).
 * - [SPARKS] : faíscas rápidas com rastro, clima energético/arcano.
 * - [MIST]   : névoa volumétrica em blobs grandes e muito lentos.
 * - [NONE]   : sem partículas (economiza bateria em aparelhos fracos).
 */
enum class ParticleType { DUST, EMBERS, SPARKS, MIST, NONE }

/**
 * Link de crédito exibido no diálogo "Portado por ..." (botão de assinatura
 * da tela). [iconKey] escolhe o ícone desenhado: "youtube", "github",
 * "telegram" ou "project" (qualquer outro valor usa um ícone de link neutro).
 */
data class PortLink(
    /** Nome do destino (ex.: "Canal no YouTube"). */
    val label: String,
    /** Linha de detalhe abaixo do nome (ex.: "@hail-games1"). */
    val description: String,
    /** URL aberta no navegador ao tocar. */
    val url: String,
    /** Chave do ícone: "youtube" | "github" | "telegram" | "project". */
    val iconKey: String,
    /** Cor do ícone e do fundo do selo do link. */
    val tint: Color,
)

/**
 * Contrato completo de branding do port. Cada campo documenta o efeito exato
 * que produz na tela, para que a personalização seja feita sem ler o layout.
 */
data class PortBrandingConfig(

    // ------------------------------------------------------------------
    // IDENTIDADE / CONTEÚDO
    // ------------------------------------------------------------------

    /** Título principal da tela (exibido grande, com halo/pulso se ativado). */
    val portTitle: String,

    /** Subtítulo curto em caixa alta acima do título (estilo "eyebrow"). */
    val portSubtitle: String,

    /** Exibe a marca vetorial [R.drawable.ic_logo_mark] acima do título. */
    val showLogo: Boolean,

    // ------------------------------------------------------------------
    // PALETA
    // ------------------------------------------------------------------

    /** Cor de acento: glow do título, orbes de luz, botão, partículas. */
    val accent: Color,

    /**
     * Variação escura do acento usada em gradientes (borda inferior do vidro,
     * camadas de profundidade). Em geral = accent com hue deslocado/mais escuro.
     */
    val accentDeep: Color,

    /** Cor das partículas quando o tipo é EMBERS/SPARKS (DUST usa cinza frio). */
    val particleColor: Color,

    // ------------------------------------------------------------------
    // ARTE DE FUNDO
    // ------------------------------------------------------------------

    /**
     * Resource da arte de fundo cinematográfica. Recomendado: JPG 1080x1920
     * em drawable-nodpi. Use `null` para fundo procedural (gradientes + orbes),
     * útil quando o port ainda não tem arte pronta.
     */
    val backgroundArtRes: Int?,

    // ------------------------------------------------------------------
    // DETECÇÃO DE DADOS DO JOGO
    // ------------------------------------------------------------------

    /**
     * Nomes de arquivo exatos procurados na pasta escolhida pelo usuário
     * (case-insensitive). O primeiro encontrado define o estado "pronto".
     * Exemplos por plataforma: "default.xex" (Xbox 360), "game.iso",
     * "SLUS_123.45" (PS1), "data.pak", "main.dol" (Wii).
     */
    val expectedDataFiles: List<String>,

    /**
     * Extensões aceitáveis como fallback: se nenhum nome exato for achado,
     * qualquer arquivo terminando com uma destas extensões valida a pasta.
     * Deixe vazia para aceitar SOMENTE os nomes exatos acima.
     */
    val acceptableExtensions: List<String>,

    // ------------------------------------------------------------------
    // ATMOSFERA / MOTION
    // ------------------------------------------------------------------

    /** Tipo do sistema de partículas ambiente. */
    val particleType: ParticleType,

    /** Quantidade base de partículas (reduzida automaticamente em telas baixas). */
    val particleCount: Int,

    /** Liga/desliga o sistema de partículas por completo. */
    val particlesEnabled: Boolean,

    /** Halo/pulso cinematográfico no título (multi-camadas, custo baixo). */
    val titleGlowEnabled: Boolean,

    /** Chip técnico (motor/GPU/ABI) no canto inferior esquerdo. */
    val showTechChip: Boolean,

    // ------------------------------------------------------------------
    // TEXTOS DA INTERFACE (PT-BR no demo; troque para localizar o port)
    // ------------------------------------------------------------------

    /** Botão primário no estado "aguardando seleção"/"não encontrado". */
    val labelSelectData: String,

    /** Botão primário no estado "dados encontrados" (dispara o jogo). */
    val labelStartGame: String,

    /** Botão secundário para escolher outra pasta. */
    val labelSelectFolder: String,

    /** Estado de status: pasta escolhida/validação em andamento. */
    val labelValidating: String,

    /** Estado de status: nenhum dado selecionado ainda (estado inicial). */
    val labelIdle: String,

    /** Dica do estado inicial — explica o que o usuário deve fazer. */
    val labelIdleHint: String,

    /** Estado de status: pasta/dados não encontrados. */
    val labelNotFound: String,

    /** Dica do estado "não encontrado"; %s é substituído pelo arquivo esperado. */
    val labelNotFoundHint: String,

    /** Estado de status: dados validados e prontos. */
    val labelFound: String,

    /** Linha complementar do estado pronto; %s = nome do arquivo detectado. */
    val labelFoundFile: String,

    /** Estado de status: permissão de leitura negada/revogada. */
    val labelPermissionError: String,

    /** Botão de fechar do diálogo de créditos. */
    val labelClose: String,

    /** Rótulo do toggle de partículas (tela de Configurações → Efeitos). */
    val labelToggleParticles: String,

    /** Rótulo do toggle "reduzir movimento" (tela de Configurações → Efeitos). */
    val labelToggleMotion: String,

    // ------------------------------------------------------------------
    // TELA DE CONFIGURAÇÕES DEDICADA (engrenagem navega para ela)
    // ------------------------------------------------------------------

    /** Título da tela de configurações (barra superior). */
    val labelSettingsTitle: String,

    /** Subtítulo da tela de configurações. */
    val labelSettingsSubtitle: String,

    /** Rodapé explicativo da tela de configurações. */
    val labelSettingsFooter: String,

    /** Botão que apaga a pasta persistida e volta ao estado inicial. */
    val labelClearSelection: String,

    // ------------------------------------------------------------------
    // CRÉDITOS / LINKS DO PORTADOR ("Portado por ...")
    // ------------------------------------------------------------------

    /** Rótulo do botão de assinatura (pill com coração). */
    val portedByLabel: String,

    /** Título do diálogo de créditos. */
    val creditsTitle: String,

    /** Subtítulo do diálogo de créditos. */
    val creditsSubtitle: String,

    /** Rodapé do diálogo de créditos. */
    val creditsFooter: String,

    /** Lista de links abertos pelo diálogo (YouTube, GitHub, Telegram, base). */
    val links: List<PortLink>,

    // ------------------------------------------------------------------
    // ACESSIBILIDADE (contentDescription)
    // ------------------------------------------------------------------

    /** Descrição do ícone de play / ação do botão primário. */
    val contentDescPlay: String,

    /** Descrição do botão de engrenagem. */
    val contentDescSettings: String,

    /** Descrição do botão secundário de pasta. */
    val contentDescFolder: String,

    /** Descrição da arte de fundo (lida por leitores de tela). */
    val contentDescBackground: String,

    /** Descrição do botão de voltar da tela de configurações. */
    val contentDescBack: String,

    /** Descrição do botão "Portado por ...". */
    val contentDescCredits: String,
)

/**
 * ===========================================================================
 *  VALORES DO PORT — DKC2Recomp (Donkey Kong Country 2, SNES)
 * ===========================================================================
 *  Identidade do port: paleta dourada de banana sobre noite de selva,
 *  névoa de fundo com ruínas e tochas (arte própria, sem arte da franquia),
 *  e o gate de ROM idêntico ao runtime nativo (USA v1.0 — ver RomStager).
 * ===========================================================================
 */
object PortBranding {

    val config: PortBrandingConfig = PortBrandingConfig(
        // ---- Identidade ---------------------------------------------------
        portTitle = "Donkey Kong Country 2",
        portSubtitle = "SNES · STATIC RECOMPILATION",
        showLogo = true,

        // ---- Paleta -------------------------------------------------------
        accent = Color(0xFFF0B44E),        // dourado quente (bananas / tochas)
        accentDeep = Color(0xFFB4701E),    // âmbar queimado das profundezas
        particleColor = Color(0xFFFFC96B), // brasas de tocha subindo

        // ---- Arte de fundo ------------------------------------------------
        // Arte cinematográfica própria (selva noturna, ruínas, névoa e
        // tochas) — evoca o clima do jogo sem reaproveitar arte da franquia.
        backgroundArtRes = com.deivid22srk.dkc2recomp.R.drawable.bg_cinematic,

        // ---- Detecção de dados --------------------------------------------
        // O port aceita UM dump exato (gate de SHA-256 do RomStager espelha
        // o runtime nativo), então a varredura por nome é vazia e ficam as
        // extensões de dump de SNES como candidatos; o hash decide.
        expectedDataFiles = emptyList(),
        acceptableExtensions = listOf(
            ".sfc", ".smc", ".fig", ".swc"
        ),

        // ---- Atmosfera ----------------------------------------------------
        particleType = ParticleType.EMBERS,
        particleCount = 54,
        particlesEnabled = true,
        titleGlowEnabled = true,
        showTechChip = true,

        // ---- Textos -------------------------------------------------------
        labelSelectData = "Selecionar ROM",
        labelStartGame = "Iniciar Jogo",
        labelSelectFolder = "Escolher Pasta de ROMs",
        labelValidating = "Verificando ROM…",
        labelIdle = "Nenhuma ROM selecionada",
        labelIdleHint = "Toque em Selecionar ROM e escolha o arquivo do jogo (DKC2 USA v1.0) ou uma pasta para varrer. Nada é procurado automaticamente.",
        labelNotFound = "ROM não reconhecida",
        labelNotFoundHint = "Use o dump suportado: DKC2 - Diddy's Kong Quest USA v1.0 (arquivo .sfc ou .smc, 4 MiB)",
        labelFound = "ROM verificada",
        labelFoundFile = "Pronto para iniciar: %s",
        labelPermissionError = "Permissão negada",
        labelClose = "Fechar",
        labelToggleParticles = "Partículas ambiente",
        labelToggleMotion = "Reduzir movimento",

        // ---- Tela de configurações ----------------------------------------
        labelSettingsTitle = "Configurações",
        labelSettingsSubtitle = "Preferências do port — persistidas no aparelho",
        labelSettingsFooter = "Opções persistidas em SharedPreferences, prontas para serem ligadas aos parâmetros do motor (PortSettings → engine). Efeitos da tela inicial valem imediatamente.",
        labelClearSelection = "Apagar ROM e seleção salvas",

        // ---- Créditos / links do portador ----------------------------------
        portedByLabel = "Portado por Hailgames",
        creditsTitle = "Hailgames",
        creditsSubtitle = "Ports Android · DKC2Recomp",
        creditsFooter = "Recompilação estática nativa do DKC2 para Android. ROM vendida separadamente; este app não distribui o jogo.",
        links = listOf(
            PortLink(
                label = "Canal no YouTube",
                description = "@hail-games1",
                url = "https://youtube.com/@hail-games1?si=rREmvIBB6s98N-2m",
                iconKey = "youtube",
                tint = Color(0xFFFF5147)
            ),
            PortLink(
                label = "GitHub",
                description = "deivid22srk · todos os repositórios",
                url = "https://github.com/deivid22srk?tab=repositories",
                iconKey = "github",
                tint = Color(0xFFE8EAF2)
            ),
            PortLink(
                label = "Telegram",
                description = "@hailgames2",
                url = "https://t.me/hailgames2",
                iconKey = "telegram",
                tint = Color(0xFF41B3E3)
            ),
            PortLink(
                label = "Projeto base deste Port",
                description = "DKC2Recomp · mstan",
                url = "https://github.com/mstan/DKC2Recomp",
                iconKey = "project",
                tint = Color(0xFFF0B44E)
            ),
        ),

        // ---- Acessibilidade ------------------------------------------------
        contentDescPlay = "Iniciar",
        contentDescSettings = "Abrir configurações",
        contentDescFolder = "Escolher outra pasta de ROMs",
        contentDescBackground = "Arte de fundo do port",
        contentDescBack = "Voltar para a tela inicial",
        contentDescCredits = "Abrir links do portador",
    )
}
