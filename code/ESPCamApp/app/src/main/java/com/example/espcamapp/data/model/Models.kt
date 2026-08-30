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
    val rotationDegrees: Int = ROTATION_NONE,
    /**
     * Cômodo vigiado por esta câmera ("Quarto do João", "Cozinha"). Vai junto
     * no `/scan` para o servidor confrontar as regras de acesso da pessoa
     * reconhecida. Vazio deixa o servidor usar o `ROOM` do `.env`.
     */
    val room: String = ""
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

/**
 * Prontidão do cadastro facial, calculada pelo servidor a partir do número de
 * amostras: `PENDENTE` até a pessoa atingir `MIN_SAMPLES_ACTIVE` amostras,
 * `ATIVO` a partir daí. Não tem relação com autorização de acesso — quem
 * decide isso é [Person.authorized] e a lista de cômodos.
 */
enum class PersonStatus { ATIVO, PENDENTE;

    val label: String get() = if (this == ATIVO) "Ativo" else "Pendente"
}

/** Cômodo cadastrado pelo administrador. */
data class Room(
    val id: String,
    val name: String
)

/**
 * Pessoa cadastrada no servidor de faces.
 *
 * Os campos do cadastro são os da tela `cadastrar-nova-pessoa.png`: amostras,
 * nome, categoria/vínculo e os cômodos liberados. Os cômodos ficam na própria
 * pessoa — não há um nível intermediário.
 */
data class Person(
    val id: String,
    val name: String,
    /** Vínculo com a casa: "Morador", "Visitante", "Prestador". */
    val category: String,
    /** Ids dos cômodos que esta pessoa pode acessar. */
    val roomIds: List<String>,
    /** Nomes dos cômodos liberados, já resolvidos pelo servidor. */
    val roomNames: List<String>,
    /** Chave mestra do cadastro: `false` bloqueia em qualquer cômodo. */
    val authorized: Boolean,
    val samples: Int,
    val status: PersonStatus,
    /** Amostras necessárias para o cadastro sair de "Pendente", segundo o servidor. */
    val minSamplesActive: Int = 0,
    val createdAtEpochSeconds: Double,
    /** Frase pronta vinda do servidor (template, não modelo de linguagem). */
    val accessLabel: String = "",
    /**
     * URL absoluta da primeira amostra facial (`/people/{id}/samples/1`), usada
     * como miniatura na lista. `null` enquanto a pessoa não tem amostra.
     */
    val thumbnailUrl: String? = null
) {
    /** Quantas amostras ainda faltam para o cadastro ficar "Ativo". */
    val missingSamples: Int
        get() = (minSamplesActive - samples).coerceAtLeast(0)

    /**
     * Explica a etiqueta "Pendente" em vez de deixá-la parada sem motivo: diz
     * quantas amostras faltam para o reconhecimento ficar confiável. `null`
     * quando o cadastro já está ativo (a etiqueta "Ativo" se explica sozinha).
     */
    val statusHint: String?
        get() = when {
            status == PersonStatus.ATIVO -> null
            missingSamples == 1 -> "falta 1 amostra"
            missingSamples > 1 -> "faltam $missingSamples amostras"
            // O servidor não informou o mínimo (versão antiga): ao menos diz o
            // que a etiqueta significa.
            else -> "poucas amostras"
        }

    /** URLs das amostras já cadastradas, na ordem em que o servidor as guarda. */
    fun sampleUrls(serverBaseUrl: String): List<String> =
        (1..samples).map { "${serverBaseUrl.trimEnd('/')}/people/$id/samples/$it" }
}

enum class DetectionStatus {
    /** Rosto reconhecido: bate com alguém cadastrado. */
    RECOGNIZED,

    /** Rosto detectado, mas ninguém corresponde — pessoa sem cadastro. */
    UNKNOWN,

    /**
     * Nenhum rosto extraído, mas os detectores auxiliares acharam alguém no
     * quadro (perfil, pouca luz, só o corpo). Antes isso virava [NO_FACE] e o
     * evento sumia do histórico.
     */
    PERSON_NO_FACE,

    /** Quadro sem ninguém. */
    NO_FACE;

    companion object {
        fun from(raw: String): DetectionStatus = when (raw.lowercase()) {
            "recognized" -> RECOGNIZED
            "person_no_face" -> PERSON_NO_FACE
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
    val source: String,
    /** Cômodo onde a câmera flagrou a pessoa. Vazio quando não foi informado. */
    val room: String = "",
    /** `null` = não havia regra de cômodo para avaliar; decidiu só o cadastro. */
    val accessAllowed: Boolean? = null,
    val accessReason: String = "",
    /**
     * Verificação de vivacidade (G1): `false` = provável foto impressa ou tela
     * mostrada à câmera. `null` = verificação desligada no servidor.
     */
    val live: Boolean? = null
) {
    /** Rótulo mostrado sob a miniatura. */
    val displayName: String
        get() = when {
            personName.isNotBlank() -> personName
            status == DetectionStatus.NO_FACE -> "Sem rosto"
            status == DetectionStatus.PERSON_NO_FACE -> "Rosto não visível"
            else -> "Sem cadastro"
        }

    /**
     * Rosto reconhecido, pessoa autorizada, cômodo liberado e rosto de gente de
     * verdade. Qualquer uma das quatro condições falhando nega o acesso.
     */
    val allowedHere: Boolean
        get() = status == DetectionStatus.RECOGNIZED &&
            authorized &&
            accessAllowed != false &&
            live != false

    /** Veredito na faixa da miniatura (design `carrosel-rostos.png`). */
    val accessLabel: String
        get() = when {
            status == DetectionStatus.NO_FACE -> "Sem rosto"
            // Há alguém, mas não dá para dizer quem: não é "autorizado" nem um
            // "não autorizado" atribuível a uma pessoa.
            status == DetectionStatus.PERSON_NO_FACE -> "Não identificado"
            status == DetectionStatus.UNKNOWN -> "Sem cadastro"
            live == false -> "Foto detectada"
            allowedHere -> "Autorizado"
            else -> "Não autorizado"
        }

    /**
     * Aviso exibido quando a pessoa foi reconhecida mas está no cômodo errado —
     * é o caso que o `authorized` do cadastro sozinho não explicaria.
     */
    val wrongRoomWarning: String?
        get() = when {
            // Tentativa de fraude vem antes: é a informação mais grave.
            live == false -> "Possível foto ou tela, não a pessoa"
            accessAllowed == false && personName.isNotBlank() ->
                if (room.isNotBlank()) "Não permitido em $room" else "Não permitido neste local"
            else -> null
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
            DetectionStatus.PERSON_NO_FACE ->
                "Há alguém no quadro, mas o rosto não pôde ser identificado"
            DetectionStatus.UNKNOWN -> "Rosto detectado, mas sem cadastro"
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
