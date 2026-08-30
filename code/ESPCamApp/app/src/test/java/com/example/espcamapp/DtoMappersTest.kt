package com.example.espcamapp

import com.example.espcamapp.data.model.Device
import com.example.espcamapp.data.model.DetectionStatus
import com.example.espcamapp.data.model.Room
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
import org.junit.Assert.assertNull
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
        val ativo = PersonDto(id = "a1", name = "João", status = "ativo", samples = 3)
            .toDomain(SERVER)
        val pendente = PersonDto(id = "b2", name = "Maria", status = "pendente", samples = 1)
            .toDomain(SERVER)

        assertEquals(PersonStatus.ATIVO, ativo.status)
        assertEquals("Ativo", ativo.status.label)
        assertEquals(PersonStatus.PENDENTE, pendente.status)
        assertEquals("Pendente", pendente.status.label)
    }

    @Test
    fun `miniatura aponta para a primeira amostra e some sem amostras`() {
        val comAmostras = PersonDto(id = "a1", samples = 3).toDomain("$SERVER/")
        val semAmostras = PersonDto(id = "b2", samples = 0).toDomain(SERVER)

        assertEquals("$SERVER/people/a1/samples/1", comAmostras.thumbnailUrl)
        assertNull(semAmostras.thumbnailUrl)
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
        val semCadastro = DetectionDto(status = "person_no_face").toDomain("http://s:8000")

        assertEquals("Sem cadastro", desconhecido.displayName)
        assertEquals("Sem rosto", semRosto.displayName)
        assertEquals("Rosto não visível", semCadastro.displayName)
    }

    @Test
    fun `presenca sem rosto e distinta de quadro vazio`() {
        // G2: era o incômodo relatado — aparecia "Sem rosto" mesmo com gente.
        val alguem = DetectionDto(status = "person_no_face").toDomain(SERVER)
        val vazio = DetectionDto(status = "no_face").toDomain(SERVER)

        assertEquals(DetectionStatus.PERSON_NO_FACE, alguem.status)
        assertEquals("Não identificado", alguem.accessLabel)
        assertEquals("Sem rosto", vazio.accessLabel)
    }

    @Test
    fun `foto detectada nega o acesso mesmo com rosto reconhecido`() {
        // G1: ataque de apresentação (foto impressa/tela).
        val spoof = DetectionDto(
            status = "recognized",
            person = "João",
            authorized = true,
            room = "Cozinha",
            accessAllowed = true,
            live = false
        ).toDomain(SERVER)

        assertFalse(spoof.allowedHere)
        assertEquals("Foto detectada", spoof.accessLabel)
        assertEquals("Possível foto ou tela, não a pessoa", spoof.wrongRoomWarning)
    }

    @Test
    fun `vivacidade desligada nao altera o veredito`() {
        val normal = DetectionDto(
            status = "recognized",
            person = "João",
            authorized = true,
            accessAllowed = true,
            live = null
        ).toDomain(SERVER)

        assertTrue(normal.allowedHere)
        assertEquals("Autorizado", normal.accessLabel)
    }

    @Test
    fun `deteccao no comodo errado vira nao autorizado com o motivo`() {
        // Cenario do tasks.md: Antonio reconhecido e autorizado, mas no quarto do Joao.
        val dto = DetectionDto(
            status = "recognized",
            person = "Antonio",
            authorized = true,
            room = "Quarto do Joao",
            accessAllowed = false,
            accessReason = "Quarto do Joao nao consta nos comodos liberados"
        )

        val detection = dto.toDomain(SERVER)

        assertFalse(detection.allowedHere)
        assertEquals("Não autorizado", detection.accessLabel)
        assertEquals("Antonio", detection.displayName)
        assertEquals("Não permitido em Quarto do Joao", detection.wrongRoomWarning)
    }

    @Test
    fun `deteccao no comodo liberado fica autorizada`() {
        val detection = DetectionDto(
            status = "recognized",
            person = "João",
            authorized = true,
            room = "Cozinha",
            accessAllowed = true
        ).toDomain(SERVER)

        assertTrue(detection.allowedHere)
        assertEquals("Autorizado", detection.accessLabel)
        assertNull(detection.wrongRoomWarning)
    }

    @Test
    fun `sem regra de comodo o veredito segue so o cadastro`() {
        // Compatibilidade: quem nao tem observacao interpretada decide pelo `authorized`.
        val autorizado = DetectionDto(status = "recognized", person = "João", authorized = true)
            .toDomain(SERVER)
        val semAutorizacao = DetectionDto(status = "recognized", person = "Ana", authorized = false)
            .toDomain(SERVER)
        val desconhecido = DetectionDto(status = "unknown").toDomain(SERVER)
        val semRosto = DetectionDto(status = "no_face").toDomain(SERVER)

        assertEquals("Autorizado", autorizado.accessLabel)
        assertNull(autorizado.wrongRoomWarning)
        assertEquals("Não autorizado", semAutorizacao.accessLabel)
        assertEquals("Sem cadastro", desconhecido.accessLabel)
        assertEquals("Sem rosto", semRosto.accessLabel)
    }

    @Test
    fun `categoria e comodos da pessoa vem prontos do servidor`() {
        // O rotulo e template no servidor, nao texto gerado por modelo.
        val morador = PersonDto(
            id = "a1",
            name = "Joao",
            category = "Morador",
            rooms = listOf("r1", "r2"),
            roomNames = listOf("Cozinha", "Sala"),
            accessLabel = "Morador: Cozinha, Sala"
        ).toDomain(SERVER)

        assertEquals("Morador", morador.category)
        assertEquals(listOf("r1", "r2"), morador.roomIds)
        assertEquals(listOf("Cozinha", "Sala"), morador.roomNames)
        assertEquals("Morador: Cozinha, Sala", morador.accessLabel)
    }

    @Test
    fun `pessoa sem comodo nao inventa acesso`() {
        val semAcesso = PersonDto(id = "b2").toDomain(SERVER)

        assertEquals(emptyList<String>(), semAcesso.roomIds)
        assertEquals("", semAcesso.accessLabel)
    }

    @Test
    fun `urls das amostras cobrem todas as fotos cadastradas`() {
        // A tela de edicao mostra as amostras que ja estao no servidor.
        val pessoa = PersonDto(id = "a1", samples = 3).toDomain(SERVER)

        assertEquals(
            listOf(
                "$SERVER/people/a1/samples/1",
                "$SERVER/people/a1/samples/2",
                "$SERVER/people/a1/samples/3"
            ),
            pessoa.sampleUrls(SERVER)
        )
        assertEquals(emptyList<String>(), PersonDto(id = "b2").toDomain(SERVER).sampleUrls(SERVER))
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
            "Rosto detectado, mas sem cadastro",
            DetectResponseDto(status = "unknown").toDomain().message
        )
        assertEquals(
            "Há alguém no quadro, mas o rosto não pôde ser identificado",
            DetectResponseDto(status = "person_no_face").toDomain().message
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

    private companion object {
        const val SERVER = "http://192.168.15.6:8000"
    }
}
