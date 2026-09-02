package com.example.espcamapp.data.repo

import com.example.espcamapp.data.model.Room
import com.example.espcamapp.data.remote.AssistantApplyDto
import com.example.espcamapp.data.remote.AssistantQuestionDto
import com.example.espcamapp.data.remote.FaceServerApi
import com.example.espcamapp.data.remote.RoomInDto
import com.example.espcamapp.data.remote.toDomain
import com.example.espcamapp.data.settings.SettingsStore
import kotlinx.coroutines.flow.first

/**
 * Cômodos da casa e assistente do administrador.
 *
 * O cadastro de cômodos é o vocabulário do controle de acesso: cada pessoa
 * marca aqui, na própria ficha, os cômodos em que pode entrar. Quem autoriza é
 * essa lista mantida pelo administrador, não uma frase interpretada por um
 * modelo.
 */
class AccessRepository(
    private val api: FaceServerApi,
    /** O modelo local leva segundos para responder: cliente paciente. */
    private val heavyApi: FaceServerApi,
    private val settings: SettingsStore
) {

    private suspend fun serverUrl(): String = settings.faceServerUrl.first()

    suspend fun rooms(): Result<List<Room>> = apiCall {
        api.rooms("${serverUrl()}/rooms").rooms.map { it.toDomain() }
    }

    suspend fun createRoom(name: String): Result<Room?> = apiCall(retries = 0) {
        api.createRoom("${serverUrl()}/rooms", RoomInDto(name)).room?.toDomain()
    }

    suspend fun renameRoom(roomId: String, name: String): Result<Room?> = apiCall(retries = 0) {
        api.patchRoom("${serverUrl()}/rooms/$roomId", RoomInDto(name)).room?.toDomain()
    }

    suspend fun deleteRoom(roomId: String): Result<Unit> =
        apiCall(retries = 0) { api.deleteRoom("${serverUrl()}/rooms/$roomId") }

    // --- Assistente do administrador (G3) ---

    /**
     * Pergunta em português. Consultas voltam respondidas; pedidos de mudança
     * voltam como proposta, que só é aplicada em [applyProposal].
     */
    suspend fun ask(question: String): Result<AssistantAnswer> = apiCall(retries = 0) {
        val resposta = heavyApi.assistantAsk(
            "${serverUrl()}/assistant/ask",
            AssistantQuestionDto(question)
        )
        AssistantAnswer(
            ok = resposta.ok,
            text = resposta.answer,
            requiresConfirmation = resposta.requiresConfirmation,
            proposal = resposta.proposal,
            detectionCount = resposta.detections.size
        )
    }

    suspend fun applyProposal(proposal: Map<String, String>): Result<String> =
        apiCall(retries = 0) {
            heavyApi.assistantApply(
                "${serverUrl()}/assistant/apply",
                AssistantApplyDto(proposal)
            ).answer
        }
}

/** Resposta do assistente já traduzida para o domínio do app. */
data class AssistantAnswer(
    val ok: Boolean,
    val text: String,
    val requiresConfirmation: Boolean,
    val proposal: Map<String, String>?,
    val detectionCount: Int
)
