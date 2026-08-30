package com.example.espcamapp.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.espcamapp.data.model.Device
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.UUID

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "espcam_settings")

/** Forma persistida de [Device] (o modelo de domínio não carrega anotações). */
@Serializable
private data class DeviceEntry(
    val id: String,
    val name: String,
    val host: String,
    val port: Int,
    val streamPort: Int,
    // Valor padrão: dispositivos gravados antes da orientação existir continuam legíveis.
    val rotationDegrees: Int = Device.ROTATION_NONE,
    // Idem para o cômodo, que chegou com as regras de acesso.
    val room: String = ""
)

private fun DeviceEntry.toDomain() =
    Device(id, name, host, port, streamPort, Device.normalizeRotation(rotationDegrees), room)

private fun Device.toEntry() =
    DeviceEntry(id, name, host, port, streamPort, rotationDegrees, room)

/**
 * Persistência local (DataStore): dispositivos ESP32 cadastrados, dispositivo
 * selecionado na tela Principal e URL do servidor Python de faces.
 */
class SettingsStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    private val entryListSerializer = ListSerializer(DeviceEntry.serializer())

    val devices: Flow<List<Device>> = context.dataStore.data.map { prefs ->
        decodeDevices(prefs[KEY_DEVICES])
    }

    val selectedDeviceId: Flow<String?> = context.dataStore.data.map { it[KEY_SELECTED_DEVICE] }

    val faceServerUrl: Flow<String> = context.dataStore.data.map {
        it[KEY_FACE_SERVER_URL] ?: DEFAULT_FACE_SERVER_URL
    }

    private fun decodeDevices(raw: String?): List<Device> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            json.decodeFromString(entryListSerializer, raw).map { it.toDomain() }
        }.getOrDefault(emptyList())
    }

    private suspend fun writeDevices(devices: List<Device>) {
        val encoded = json.encodeToString(entryListSerializer, devices.map { it.toEntry() })
        context.dataStore.edit { it[KEY_DEVICES] = encoded }
    }

    /** Adiciona um dispositivo e o seleciona. Retorna o [Device] já com id. */
    suspend fun addDevice(
        name: String,
        host: String,
        port: Int,
        streamPort: Int,
        room: String = ""
    ): Device {
        val device = Device(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            host = host.trim(),
            port = port,
            streamPort = streamPort,
            room = room.trim()
        )
        context.dataStore.edit { prefs ->
            val current = decodeDevices(prefs[KEY_DEVICES])
            prefs[KEY_DEVICES] = json.encodeToString(
                entryListSerializer,
                (current + device).map { it.toEntry() }
            )
            prefs[KEY_SELECTED_DEVICE] = device.id
        }
        return device
    }

    /**
     * Grava as alterações de um dispositivo já cadastrado (nome, endereço,
     * portas, orientação). O `id` é preservado, então a seleção atual e o
     * histórico de status continuam válidos.
     */
    suspend fun updateDevice(device: Device) {
        val sanitized = device.copy(
            name = device.name.trim(),
            host = device.host.trim(),
            rotationDegrees = Device.normalizeRotation(device.rotationDegrees),
            room = device.room.trim()
        )
        context.dataStore.edit { prefs ->
            val updated = decodeDevices(prefs[KEY_DEVICES])
                .map { if (it.id == sanitized.id) sanitized else it }
            prefs[KEY_DEVICES] = json.encodeToString(entryListSerializer, updated.map { it.toEntry() })
        }
    }

    suspend fun removeDevice(deviceId: String) {
        context.dataStore.edit { prefs ->
            val remaining = decodeDevices(prefs[KEY_DEVICES]).filterNot { it.id == deviceId }
            prefs[KEY_DEVICES] = json.encodeToString(entryListSerializer, remaining.map { it.toEntry() })
            if (prefs[KEY_SELECTED_DEVICE] == deviceId) {
                val next = remaining.firstOrNull()?.id
                if (next == null) prefs.remove(KEY_SELECTED_DEVICE) else prefs[KEY_SELECTED_DEVICE] = next
            }
        }
    }

    suspend fun selectDevice(deviceId: String) {
        context.dataStore.edit { it[KEY_SELECTED_DEVICE] = deviceId }
    }

    suspend fun setFaceServerUrl(url: String) {
        context.dataStore.edit { it[KEY_FACE_SERVER_URL] = normalizeUrl(url) }
    }

    companion object {
        const val DEFAULT_FACE_SERVER_URL = "http://192.168.15.10:8000"

        private val KEY_DEVICES = stringPreferencesKey("devices")
        private val KEY_SELECTED_DEVICE = stringPreferencesKey("selected_device")
        private val KEY_FACE_SERVER_URL = stringPreferencesKey("face_server_url")

        /** Aceita "192.168.15.10:8000" e completa o esquema; remove a barra final. */
        fun normalizeUrl(raw: String): String {
            val trimmed = raw.trim().trimEnd('/')
            if (trimmed.isEmpty()) return DEFAULT_FACE_SERVER_URL
            return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                trimmed
            } else {
                "http://$trimmed"
            }
        }
    }
}
