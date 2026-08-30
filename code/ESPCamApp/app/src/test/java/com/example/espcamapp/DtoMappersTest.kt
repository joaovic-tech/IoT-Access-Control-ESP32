package com.example.espcamapp

import com.example.espcamapp.data.model.Device
import com.example.espcamapp.data.model.DetectionStatus
import com.example.espcamapp.data.model.PersonStatus
import com.example.espcamapp.data.remote.DetectResponseDto
import com.example.espcamapp.data.remote.DetectedFaceDto
import com.example.espcamapp.data.remote.DetectionDto
import com.example.espcamapp.data.remote.Esp32StatusDto
import com.example.espcamapp.data.remote.Http
import com.example.espcamapp.data.remote.PersonDto
import com.example.espcamapp.data.remote.toDomain
import com.example.espcamapp.data.settings.SettingsStore
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Mapeadores DTO -> domínio e desserialização dos payloads reais. */
class DtoMappersTest {

    private val json: Json = Http.json

    @Test
    fun `status do firmware vira DeviceStatus`() {
        // Payload igual ao que api_status_handler monta no app_httpd.cpp.
        val payload = """
            {"status":"online","fw":"1.1.0","ip":"192.168.15.72","rssi":-58,"uptime_s":420,
             "flash":true,"flash_duty":255,"relay":false,"pir":true,"pir_count":7,
             "free_heap":123456,"free_psram":2000000,"has_psram":true,
             "framesize":8,"quality":12,"fps":18.4,"stream_clients":1,
             "detect_busy":false,"detect_last_status":200}
        """.trimIndent()

        val status = json.decodeFromString<Esp32StatusDto>(payload).toDomain()

        assertEquals("1.1.0", status.firmware)
        assertEquals(-58, status.rssi)
        assertEquals(420L, status.uptimeSeconds)
        assertTrue(status.flashOn)
        assertFalse(status.relayOn)
        assertTrue(status.pirActive)
        assertEquals(18.4, status.fps, 0.001)
        assertEquals(1, status.streamClients)
        assertTrue(status.hasPsram)
    }

    @Test
    fun `campos ausentes no status nao quebram o mapeamento`() {
        val status = json.decodeFromString<Esp32StatusDto>("""{"fw":"0.9.0"}""").toDomain()
        assertEquals("0.9.0", status.firmware)
        assertEquals(0, status.streamClients)
    }

    @Test
    fun `pessoa ativa e pendente conforme o status do servidor`() {
        val ativo = PersonDto(id = "a1", name = "João", status = "ativo", samples = 3).toDomain()
        val pendente = PersonDto(id = "b2", name = "Maria", status = "pendente", samples = 1).toDomain()

        assertEquals(PersonStatus.ATIVO, ativo.status)
        assertEquals("Ativo", ativo.status.label)
        assertEquals(PersonStatus.PENDENTE, pendente.status)
        assertEquals("Pendente", pendente.status.label)
    }

    @Test
    fun `categoria vazia cai para Visitante`() {
        assertEquals("Visitante", PersonDto(id = "x", category = "").toDomain().category)
    }

    @Test
    fun `deteccao resolve a url relativa contra a base do servidor`() {
        val dto = DetectionDto(
            id = "20260829_153402_recognized_joao-victor",
            file = "20260829_153402_recognized_joao-victor.jpg",
            url = "/media/detections/20260829_153402_recognized_joao-victor.jpg",
            timeLabel = "29/08/2026 15:34:02",
            status = "recognized",
            person = "João Victor",
            category = "Morador",
            authorized = true,
            source = "pir"
        )

        val detection = dto.toDomain("http://192.168.15.10:8000/")

        assertEquals(
            "http://192.168.15.10:8000/media/detections/20260829_153402_recognized_joao-victor.jpg",
            detection.imageUrl
        )
        assertEquals(DetectionStatus.RECOGNIZED, detection.status)
        assertEquals("João Victor", detection.displayName)
    }

    @Test
    fun `deteccao sem pessoa usa rotulo generico`() {
        val desconhecido = DetectionDto(status = "unknown").toDomain("http://s:8000")
        val semRosto = DetectionDto(status = "no_face").toDomain("http://s:8000")

        assertEquals("Desconhecido", desconhecido.displayName)
        assertEquals("Sem rosto", semRosto.displayName)
    }

    @Test
    fun `mensagem do scan reflete o resultado`() {
        val reconhecido = DetectResponseDto(
            status = "recognized",
            detected = listOf(DetectedFaceDto(personId = "a1", name = "João", authorized = true)),
            unlocked = true
        ).toDomain()
        assertEquals("João reconhecido — acesso liberado", reconhecido.message)

        assertEquals(
            "Nenhum rosto encontrado na captura",
            DetectResponseDto(status = "no_face").toDomain().message
        )
        assertEquals(
            "Rosto não reconhecido",
            DetectResponseDto(status = "unknown").toDomain().message
        )
    }

    @Test
    fun `urls do dispositivo seguem as portas configuradas`() {
        val device = Device(id = "1", name = "Quarto", host = "192.168.15.72", port = 80, streamPort = 81)
        assertEquals("http://192.168.15.72:80/api/status", "${device.baseUrl}/api/status")
        assertEquals("http://192.168.15.72:81/stream", device.streamUrl)
        assertEquals("http://192.168.15.72:80/capture", device.captureUrl)
    }

    @Test
    fun `normalizeUrl completa o esquema e tira a barra final`() {
        assertEquals("http://192.168.15.10:8000", SettingsStore.normalizeUrl("192.168.15.10:8000"))
        assertEquals("http://192.168.15.10:8000", SettingsStore.normalizeUrl("http://192.168.15.10:8000/"))
        assertEquals("https://faces.local", SettingsStore.normalizeUrl(" https://faces.local "))
        assertEquals(SettingsStore.DEFAULT_FACE_SERVER_URL, SettingsStore.normalizeUrl("   "))
    }

    @Test
    fun `normalizeRotation aceita so multiplos de 90 e volta ao padrao`() {
        assertEquals(0, Device.normalizeRotation(0))
        assertEquals(180, Device.normalizeRotation(180))
        assertEquals(270, Device.normalizeRotation(-90))
        assertEquals(90, Device.normalizeRotation(450))
        // Valores fora da lista caem para "sem giro" em vez de quebrar o player.
        assertEquals(Device.ROTATION_NONE, Device.normalizeRotation(45))
        assertEquals(Device.ROTATION_NONE, Device.normalizeRotation(1000))
    }

    @Test
    fun `sameAddressAs ignora nome e orientacao`() {
        val original = Device(id = "1", name = "Quarto", host = "192.168.15.72", port = 80, streamPort = 81)
        // Renomear e girar não é mudança de endereço: não deve exigir teste de conexão.
        assertTrue(original.sameAddressAs(original.copy(name = "Sala", rotationDegrees = 180)))
        assertFalse(original.sameAddressAs(original.copy(host = "192.168.15.93")))
        assertFalse(original.sameAddressAs(original.copy(port = 8080)))
        assertFalse(original.sameAddressAs(original.copy(streamPort = 82)))
    }
}
