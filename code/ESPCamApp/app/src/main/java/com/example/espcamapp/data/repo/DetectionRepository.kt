package com.example.espcamapp.data.repo

import com.example.espcamapp.data.model.Detection
import com.example.espcamapp.data.model.ScanResult
import com.example.espcamapp.data.remote.FaceServerApi
import com.example.espcamapp.data.remote.ScanRequestDto
import com.example.espcamapp.data.remote.toDomain
import com.example.espcamapp.data.settings.SettingsStore
import kotlinx.coroutines.flow.first

/** Histórico de detecções e disparo manual do reconhecimento ("Escanear ambiente"). */
class DetectionRepository(
    private val api: FaceServerApi,
    /** Cliente de timeout longo — o reconhecimento leva segundos. */
    private val heavyApi: FaceServerApi,
    private val settings: SettingsStore
) {

    private suspend fun serverUrl(): String = settings.faceServerUrl.first()

    suspend fun recent(limit: Int = 12): Result<List<Detection>> = apiCall {
        val base = serverUrl()
        api.detections("$base/detections", limit).items.map { it.toDomain(base) }
    }

    /**
     * Pede ao servidor que puxe um frame do ESP32 e rode o reconhecimento.
     * Sem retry: uma captura duplicada geraria duas entradas no histórico.
     *
     * `rotationDegrees` é a orientação cadastrada para o dispositivo: o servidor
     * gira o quadro antes de detectar, senão uma câmera invertida nunca acha
     * rosto (o detector do dlib não reconhece rosto de cabeça para baixo).
     *
     * `room` é o cômodo vigiado pela câmera: com ele o servidor confronta a
     * regra de acesso da pessoa e nega quem está no lugar errado.
     */
    suspend fun scan(
        esp32Url: String?,
        rotationDegrees: Int,
        room: String
    ): Result<ScanResult> =
        apiCall(retries = 0) {
            heavyApi.scan(
                "${serverUrl()}/scan",
                ScanRequestDto(
                    esp32Url = esp32Url,
                    rotation = rotationDegrees,
                    room = room
                )
            ).toDomain()
        }
}
