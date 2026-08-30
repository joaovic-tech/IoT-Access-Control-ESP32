package com.example.espcamapp.data.model

/**
 * Modelos de domínio do app. Não têm anotações de serialização: os DTOs da
 * camada `data/remote` são convertidos para cá pelos mapeadores.
 */

/** Um ESP32-CAM cadastrado pelo usuário e salvo no [com.example.espcamapp.data.settings.SettingsStore]. */
data class Device(
    val id: String,
    val name: String,
    val host: String,
    val port: Int = DEFAULT_PORT,
    val streamPort: Int = DEFAULT_STREAM_PORT,
    /**
     * Giro aplicado na exibição do vídeo, em graus no sentido horário
     * (0/90/180/270). Corrige a câmera montada de lado ou de cabeça para baixo
     * sem mexer no firmware — é só apresentação, o ESP32 segue enviando o
     * quadro na orientação original.
     */
    val rotationDegrees: Int = ROTATION_NONE
) {
    /** Base das rotas REST (`/api/status`, `/api/flash`, ...). */
    val baseUrl: String get() = "http://$host:$port"

    /** Stream MJPEG contínuo (porta separada no firmware). */
    val streamUrl: String get() = "http://$host:$streamPort/stream"

    /** Captura JPEG sob demanda. */
    val captureUrl: String get() = "$baseUrl/capture"

    val addressLabel: String get() = "IP: $host:$port"

    /** Mesmo endereço? Usado para decidir se vale testar a conexão ao editar. */
    fun sameAddressAs(other: Device): Boolean =
        host == other.host && port == other.port && streamPort == other.streamPort

    companion object {
        const val DEFAULT_PORT = 80
        const val DEFAULT_STREAM_PORT = 81

        const val ROTATION_NONE = 0

        /** Opções oferecidas na tela Dispositivos. */
        val ROTATION_OPTIONS = listOf(0, 90, 180, 270)

        fun rotationLabel(degrees: Int): String = when (normalizeRotation(degrees)) {
            0 -> "Normal"
            90 -> "90° ↻"
            180 -> "De cabeça para baixo"
            else -> "270° ↺"
        }

        /** Garante um dos quatro múltiplos de 90 aceitos pelo player. */
        fun normalizeRotation(degrees: Int): Int {
            val wrapped = ((degrees % 360) + 360) % 360
            return if (wrapped in ROTATION_OPTIONS) wrapped else ROTATION_NONE
        }
    }
}

/** Resposta de `GET /api/status` do firmware. */
data class DeviceStatus(
    val firmware: String,
    val ip: String,
    val rssi: Int,
    val uptimeSeconds: Long,
    val flashOn: Boolean,
    val flashDuty: Int,
    val relayOn: Boolean,
    val pirActive: Boolean,
    val pirCount: Long,
    val freeHeap: Long,
    val hasPsram: Boolean,
    val fps: Double,
    val streamClients: Int
)

enum class PersonStatus { ATIVO, PENDENTE;

    val label: String get() = if (this == ATIVO) "Ativo" else "Pendente"
}

/** Pessoa cadastrada no servidor de faces. */
data class Person(
    val id: String,
    val name: String,
    val category: String,
    val authorized: Boolean,
    val note: String,
    val samples: Int,
    val status: PersonStatus,
    val createdAtEpochSeconds: Double
)

enum class DetectionStatus {
    RECOGNIZED, UNKNOWN, NO_FACE;

    companion object {
        fun from(raw: String): DetectionStatus = when (raw.lowercase()) {
            "recognized" -> RECOGNIZED
            "no_face" -> NO_FACE
            else -> UNKNOWN
        }
    }
}

/** Item do histórico "Rostos detectados recentes". */
data class Detection(
    val id: String,
    /** URL absoluta da imagem, já resolvida contra a base do servidor de faces. */
    val imageUrl: String,
    val timeLabel: String,
    val status: DetectionStatus,
    val personName: String,
    val category: String,
    val authorized: Boolean,
    val source: String
) {
    /** Rótulo mostrado sob a miniatura. */
    val displayName: String
        get() = when {
            personName.isNotBlank() -> personName
            status == DetectionStatus.NO_FACE -> "Sem rosto"
            else -> "Desconhecido"
        }
}

/** Resultado de `POST /detect` ou `POST /scan`. */
data class ScanResult(
    val status: DetectionStatus,
    val names: List<String>,
    val unlocked: Boolean
) {
    /** Mensagem curta para o snackbar da tela Principal. */
    val message: String
        get() = when (status) {
            DetectionStatus.NO_FACE -> "Nenhum rosto encontrado na captura"
            DetectionStatus.UNKNOWN -> "Rosto não reconhecido"
            DetectionStatus.RECOGNIZED -> {
                val who = names.filter { it.isNotBlank() }.joinToString(", ")
                if (unlocked) "$who reconhecido — acesso liberado" else "$who reconhecido"
            }
        }
}

/** Saúde do servidor Python de faces. */
data class ServerHealth(
    val ok: Boolean,
    val knownPeople: Int,
    val knownSamples: Int,
    val engineAvailable: Boolean,
    val engineError: String
)
