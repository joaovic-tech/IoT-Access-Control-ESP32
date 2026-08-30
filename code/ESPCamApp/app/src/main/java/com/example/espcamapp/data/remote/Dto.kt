package com.example.espcamapp.data.remote

import com.example.espcamapp.data.model.Detection
import com.example.espcamapp.data.model.DetectionStatus
import com.example.espcamapp.data.model.DeviceStatus
import com.example.espcamapp.data.model.Person
import com.example.espcamapp.data.model.PersonStatus
import com.example.espcamapp.data.model.ScanResult
import com.example.espcamapp.data.model.ServerHealth
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DTOs das APIs do ESP32-CAM e do servidor Python, com os mapeadores para os
 * modelos de domínio. Campos ausentes usam o valor padrão, então uma versão
 * mais antiga do firmware/servidor não quebra o app.
 */

// ==========================================================================
// ESP32-CAM
// ==========================================================================
@Serializable
data class Esp32StatusDto(
    val status: String = "online",
    val fw: String = "?",
    val ip: String = "",
    val rssi: Int = 0,
    @SerialName("uptime_s") val uptimeS: Long = 0,
    val flash: Boolean = false,
    @SerialName("flash_duty") val flashDuty: Int = 0,
    val relay: Boolean = false,
    val pir: Boolean = false,
    @SerialName("pir_count") val pirCount: Long = 0,
    @SerialName("free_heap") val freeHeap: Long = 0,
    @SerialName("has_psram") val hasPsram: Boolean = false,
    val fps: Double = 0.0,
    @SerialName("stream_clients") val streamClients: Int = 0
)

fun Esp32StatusDto.toDomain(): DeviceStatus = DeviceStatus(
    firmware = fw,
    ip = ip,
    rssi = rssi,
    uptimeSeconds = uptimeS,
    flashOn = flash,
    flashDuty = flashDuty,
    relayOn = relay,
    pirActive = pir,
    pirCount = pirCount,
    freeHeap = freeHeap,
    hasPsram = hasPsram,
    fps = fps,
    streamClients = streamClients
)

@Serializable
data class Esp32ActionDto(
    val success: Boolean = false,
    val flash: Boolean = false,
    val relay: Boolean = false,
    val message: String = ""
)

@Serializable
data class Esp32ScanDto(
    val queued: Boolean = false,
    val busy: Boolean = false,
    @SerialName("last_status") val lastStatus: Int = 0
)

// ==========================================================================
// Servidor Python de faces
// ==========================================================================
@Serializable
data class PersonDto(
    val id: String,
    val name: String = "",
    val category: String = "",
    val authorized: Boolean = false,
    val note: String = "",
    val samples: Int = 0,
    val status: String = "pendente",
    @SerialName("created_at") val createdAt: Double = 0.0
)

fun PersonDto.toDomain(): Person = Person(
    id = id,
    name = name,
    category = category.ifBlank { "Visitante" },
    authorized = authorized,
    note = note,
    samples = samples,
    status = if (status.equals("ativo", ignoreCase = true)) {
        PersonStatus.ATIVO
    } else {
        PersonStatus.PENDENTE
    },
    createdAtEpochSeconds = createdAt
)

@Serializable
data class PeopleResponseDto(val people: List<PersonDto> = emptyList())

@Serializable
data class PersonResponseDto(
    val person: PersonDto? = null,
    val warnings: List<String> = emptyList()
)

@Serializable
data class PersonPatchDto(
    val name: String? = null,
    val category: String? = null,
    val authorized: Boolean? = null,
    val note: String? = null
)

@Serializable
data class DetectionDto(
    val id: String = "",
    val file: String = "",
    val url: String = "",
    val time: Double = 0.0,
    @SerialName("time_label") val timeLabel: String = "",
    val status: String = "unknown",
    val person: String = "",
    @SerialName("person_id") val personId: String? = null,
    val category: String = "",
    val authorized: Boolean = false,
    val source: String = ""
)

/** [serverBaseUrl] resolve a URL relativa `/media/detections/...` para absoluta. */
fun DetectionDto.toDomain(serverBaseUrl: String): Detection = Detection(
    id = id.ifBlank { file },
    imageUrl = serverBaseUrl.trimEnd('/') + url,
    timeLabel = timeLabel,
    status = DetectionStatus.from(status),
    personName = person,
    category = category,
    authorized = authorized,
    source = source
)

@Serializable
data class DetectionsResponseDto(
    val total: Int = 0,
    val items: List<DetectionDto> = emptyList()
)

@Serializable
data class DetectedFaceDto(
    @SerialName("person_id") val personId: String? = null,
    val name: String = "",
    val authorized: Boolean = false,
    val distance: Double = 1.0
)

@Serializable
data class DetectResponseDto(
    val status: String = "unknown",
    val detected: List<DetectedFaceDto> = emptyList(),
    val url: String = "",
    val unlocked: Boolean = false
)

fun DetectResponseDto.toDomain(): ScanResult = ScanResult(
    status = DetectionStatus.from(status),
    names = detected.map { it.name },
    unlocked = unlocked
)

@Serializable
data class ScanRequestDto(
    @SerialName("esp32_url") val esp32Url: String? = null,
    /** Giro aplicado pelo servidor antes de detectar (0/90/180/270). */
    val rotation: Int = 0
)

@Serializable
data class EnrollRequestDto(
    @SerialName("person_id") val personId: String,
    @SerialName("esp32_url") val esp32Url: String? = null,
    val rotation: Int = 0
)

@Serializable
data class HealthDto(
    val status: String = "degraded",
    @SerialName("known_people") val knownPeople: Int = 0,
    @SerialName("known_samples") val knownSamples: Int = 0,
    @SerialName("engine_available") val engineAvailable: Boolean = false,
    @SerialName("engine_error") val engineError: String = ""
)

fun HealthDto.toDomain(): ServerHealth = ServerHealth(
    ok = status.equals("ok", ignoreCase = true),
    knownPeople = knownPeople,
    knownSamples = knownSamples,
    engineAvailable = engineAvailable,
    engineError = engineError
)
