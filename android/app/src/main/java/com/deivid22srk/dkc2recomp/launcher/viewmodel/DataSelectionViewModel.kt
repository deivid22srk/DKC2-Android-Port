package com.deivid22srk.dkc2recomp.launcher.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.deivid22srk.dkc2recomp.launcher.config.PortBranding
import com.deivid22srk.dkc2recomp.launcher.data.GameDataScanner
import com.deivid22srk.dkc2recomp.launcher.data.RomStager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel da tela de seleção de dados do port DKC2 — arquitetura
 * estado/UI separada, adaptada do template para o contrato do motor nativo:
 *
 *   SAF arquivo (Activity) ─▶ onFilePicked()   ─▶ RomStager (cópia + SHA-256) ─▶ UiState
 *   SAF pasta  (Activity)  ─▶ onFolderPicked() ─▶ varredura + RomStager       ─▶ UiState
 *   ROM já staged (boot)   ─▶ verificação silenciosa do hash                  ─▶ UiState
 *
 * O app abre SEMPRE em [DataPhase.Idle] ou no estado "pronto" restaurado:
 * nada é procurado automaticamente. A ROM é validada com o MESMO gate do
 * runtime nativo (DKC2 USA v1.0, 4 MiB, SHA-256 fixado — ver [RomStager]),
 * então "Dados prontos" significa "o motor aceita este arquivo".
 *
 * Ordem de restauração no boot (sem depender de SAF):
 *  1. `filesDir/rom.sfc` staged e com hash válido → Found direto;
 *  2. pasta persistida de sessão anterior → revalidada silenciosamente;
 *  3. nada → Idle, o usuário escolhe a ROM.
 *
 * A UI nunca toca no ContentResolver: ela apenas renderiza [uiState] e emite
 * eventos. Trocar Compose por Views não muda nada aqui.
 */
class DataSelectionViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(DataSelectionUiState())
    val uiState: StateFlow<DataSelectionUiState> = _uiState.asStateFlow()

    /**
     * Ponto de integração do MOTOR do port. O host (LauncherActivity)
     * registra um callback aqui e recebe (folderUri, fileName) quando o
     * usuário toca em "Iniciar Jogo" — a ROM já está staged e verificada.
     */
    var onLaunchGame: ((folderUri: String, fileName: String) -> Unit)? = null

    init {
        restorePreviousSelection()
    }

    /**
     * Fluxo de boot (sem busca automática):
     *  - ROM staged com hash válido  → Found imediato (caminho rápido);
     *  - ROM staged corrompida       → removida (autocura) e segue para a pasta;
     *  - pasta persistida existente  → validação silenciosa rápida;
     *  - nada salvo                  → Idle, direto ao ponto.
     */
    private fun restorePreviousSelection() {
        viewModelScope.launch {
            val stagedOk = withContext(Dispatchers.IO) {
                RomStager.isStagedRomValid(getApplication())
            }
            if (stagedOk) {
                _uiState.value = DataSelectionUiState(
                    DataPhase.Found(folderUri = "", fileName = stagedDisplayName())
                )
                return@launch
            }
            // Autocura: um staging corrompido/truncado nunca é reutilizado.
            withContext(Dispatchers.IO) { RomStager.clearStaged(getApplication()) }
            prefs.edit().remove(KEY_STAGED_NAME).apply()

            val savedUri = prefs.getString(KEY_FOLDER_URI, null) ?: return@launch
            validateFolder(Uri.parse(savedUri))
        }
    }

    /** Chamado pela Activity quando o SAF devolve o arquivo de ROM escolhido. */
    fun onFilePicked(uri: Uri, displayName: String) {
        _uiState.value = DataSelectionUiState(DataPhase.Validating)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    RomStager.verifyAndStage(getApplication(), uri, displayName)
                }.getOrNull() ?: RomStager.StageResult.Unsupported
            }
            _uiState.value = when (result) {
                is RomStager.StageResult.Staged -> {
                    prefs.edit().putString(KEY_STAGED_NAME, result.displayName).apply()
                    DataSelectionUiState(DataPhase.Found(folderUri = "", fileName = result.displayName))
                }
                RomStager.StageResult.Unsupported -> DataSelectionUiState(DataPhase.NotFound)
            }
        }
    }

    /** Chamado pela Activity quando o SAF devolve a árvore (pasta) escolhida. */
    fun onFolderPicked(treeUri: Uri) {
        val app = getApplication<Application>()
        // Persistir o grant para sobreviver a reboots (SAF padrão de ports).
        try {
            app.contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) {
            _uiState.value = DataSelectionUiState(DataPhase.PermissionError)
            return
        }
        prefs.edit().putString(KEY_FOLDER_URI, treeUri.toString()).apply()
        validateFolder(treeUri)
    }

    /** Revalida a pasta atualmente persistida (ex.: arquivo chegou depois). */
    fun rescan() {
        prefs.getString(KEY_FOLDER_URI, null)?.let { validateFolder(Uri.parse(it)) }
    }

    /**
     * Varre a pasta em Dispatchers.IO (listFiles em SAF é I/O de verdade) e
     * stageia o primeiro candidato que passar no gate nativo — isso resolve
     * pastas com várias ROMs: apenas o dump suportado valida a seleção.
     */
    private fun validateFolder(uri: Uri) {
        _uiState.value = DataSelectionUiState(DataPhase.Validating)
        viewModelScope.launch {
            val staged = withContext(Dispatchers.IO) {
                runCatching {
                    val folder = DocumentFile.fromTreeUri(getApplication(), uri)
                        ?: return@runCatching null
                    GameDataScanner.findCandidates(folder, PortBranding.config)
                        .asSequence()
                        .mapNotNull { candidate ->
                            val name = candidate.name ?: return@mapNotNull null
                            val result = RomStager.verifyAndStage(
                                getApplication(), candidate.uri, name
                            )
                            (result as? RomStager.StageResult.Staged)?.displayName
                        }
                        .firstOrNull()
                }.getOrNull()
            }
            _uiState.value = if (staged != null) {
                prefs.edit().putString(KEY_STAGED_NAME, staged).apply()
                DataSelectionUiState(DataPhase.Found(folderUri = uri.toString(), fileName = staged))
            } else {
                DataSelectionUiState(DataPhase.NotFound)
            }
        }
    }

    /**
     * Apaga a seleção salva (usado pela tela de Configurações): remove a ROM
     * staged, libera a permissão persistida da pasta, limpa os prefs e volta
     * ao estado inicial.
     */
    fun clearSavedSelection() {
        val app = getApplication<Application>()
        RomStager.clearStaged(app)
        prefs.getString(KEY_FOLDER_URI, null)?.let { saved ->
            try {
                app.contentResolver.releasePersistableUriPermission(
                    Uri.parse(saved),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
                // Permissão já revogada pelo sistema — nada a fazer.
            }
        }
        prefs.edit().remove(KEY_FOLDER_URI).remove(KEY_STAGED_NAME).apply()
        _uiState.value = DataSelectionUiState(DataPhase.Idle)
    }

    /** Clique no botão primário com dados prontos — entrega ao motor do port. */
    fun onStartGame() {
        val phase = _uiState.value.phase
        if (phase is DataPhase.Found) {
            onLaunchGame?.invoke(phase.folderUri, phase.fileName)
                ?: run {
                    // Demo sem motor acoplado: mostra o contrato recebido.
                    Toast.makeText(
                        getApplication(),
                        "Motor do port receberia: ${phase.fileName}",
                        Toast.LENGTH_LONG
                    ).show()
                }
        }
    }

    private fun stagedDisplayName(): String =
        prefs.getString(KEY_STAGED_NAME, null) ?: "rom.sfc (verificada)"

    private companion object {
        const val PREFS_NAME = "port_screen_prefs"
        const val KEY_FOLDER_URI = "data_folder_uri"
        const val KEY_STAGED_NAME = "staged_rom_name"
    }
}
