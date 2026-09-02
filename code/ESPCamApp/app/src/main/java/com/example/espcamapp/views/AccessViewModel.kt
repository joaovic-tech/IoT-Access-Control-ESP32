package com.example.espcamapp.views

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.espcamapp.appContainer
import com.example.espcamapp.data.model.Room
import com.example.espcamapp.data.repo.AccessRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AccessUiState(
    val rooms: List<Room> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    // --- Assistente (G3) ---
    val question: String = "",
    val asking: Boolean = false,
    val answer: String? = null,
    /** Proposta aguardando o OK do administrador. Nada muda até ele confirmar. */
    val pendingProposal: Map<String, String>? = null
)

/** Estado da tela de Cômodos da casa e do assistente. */
class AccessViewModel(private val repository: AccessRepository) : ViewModel() {

    private val _state = MutableStateFlow(AccessUiState())
    val state: StateFlow<AccessUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true)
            val comodos = repository.rooms()
            _state.value = _state.value.copy(
                rooms = comodos.getOrDefault(_state.value.rooms),
                loading = false,
                error = comodos.exceptionOrNull()?.message
            )
        }
    }

    fun createRoom(name: String) = executar("Cômodo criado") {
        repository.createRoom(name.trim())
    }

    fun renameRoom(room: Room, name: String) = executar("Cômodo renomeado") {
        repository.renameRoom(room.id, name.trim())
    }

    fun deleteRoom(room: Room) = executar("${room.name} removido") {
        repository.deleteRoom(room.id)
    }

    /**
     * Roda a operação, mostra o erro do servidor quando falha e recarrega. As
     * mensagens de conflito (nome repetido) vêm do próprio servidor, que é
     * quem conhece a regra.
     */
    private fun executar(sucesso: String?, bloco: suspend () -> Result<*>) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true)
            val resultado = bloco()
            _state.value = _state.value.copy(
                loading = false,
                message = resultado.fold(
                    { sucesso },
                    { it.message ?: "Não foi possível concluir" }
                )
            )
            refresh()
        }
    }

    fun setQuestion(value: String) {
        _state.value = _state.value.copy(question = value)
    }

    /** Pergunta ao assistente; a resposta pode ser texto ou uma proposta. */
    fun ask() {
        val pergunta = _state.value.question.trim()
        if (pergunta.isBlank()) return
        viewModelScope.launch {
            _state.value = _state.value.copy(asking = true, answer = null, pendingProposal = null)
            repository.ask(pergunta).fold(
                onSuccess = { resposta ->
                    val texto = if (resposta.detectionCount > 0) {
                        "${resposta.text} (${resposta.detectionCount} no histórico)"
                    } else {
                        resposta.text
                    }
                    _state.value = _state.value.copy(
                        asking = false,
                        answer = texto,
                        pendingProposal = resposta.proposal.takeIf { resposta.requiresConfirmation }
                    )
                },
                onFailure = {
                    _state.value = _state.value.copy(
                        asking = false,
                        answer = it.message ?: "Falha ao falar com o assistente"
                    )
                }
            )
        }
    }

    /** Aplica a proposta pendente — só chega aqui depois do OK na tela. */
    fun confirmProposal() {
        val proposta = _state.value.pendingProposal ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(asking = true)
            val resultado = repository.applyProposal(proposta)
            _state.value = _state.value.copy(
                asking = false,
                pendingProposal = null,
                answer = null,
                question = "",
                message = resultado.fold({ it }, { it.message ?: "Não foi possível aplicar" })
            )
            refresh()
        }
    }

    fun discardProposal() {
        _state.value = _state.value.copy(pendingProposal = null, answer = null)
    }

    fun consumeMessage() {
        _state.value = _state.value.copy(message = null)
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { AccessViewModel(appContainer.accessRepository) }
        }
    }
}
