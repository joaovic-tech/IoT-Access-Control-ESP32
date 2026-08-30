package com.example.espcamapp.data.repo

import com.example.espcamapp.data.model.Device
import com.example.espcamapp.data.model.DeviceStatus
import com.example.espcamapp.data.remote.Esp32Api
import com.example.espcamapp.data.remote.toDomain
import com.example.espcamapp.data.settings.SettingsStore
import kotlinx.coroutines.flow.Flow

/** Acesso aos ESP32-CAM cadastrados: leitura de status e controle dos atuadores. */
class DeviceRepository(
    private val api: Esp32Api,
    private val settings: SettingsStore
) {

    val devices: Flow<List<Device>> = settings.devices
    val selectedDeviceId: Flow<String?> = settings.selectedDeviceId

    suspend fun status(device: Device): Result<DeviceStatus> =
        apiCall { api.status("${device.baseUrl}/api/status").toDomain() }

    suspend fun setFlash(device: Device, on: Boolean): Result<Boolean> =
        apiCall { api.flash("${device.baseUrl}/api/flash", if (on) 1 else 0).flash }

    suspend fun setRelay(device: Device, on: Boolean): Result<Boolean> =
        apiCall { api.relay("${device.baseUrl}/api/relay", if (on) 1 else 0).relay }

    /** Pulso de 3 s no relé — libera o acesso físico. */
    suspend fun unlock(device: Device): Result<Unit> =
        apiCall { api.unlock("${device.baseUrl}/api/unlock") }.map { }

    /** Pede ao próprio ESP32 que capture e poste no servidor de faces. */
    suspend fun requestDeviceScan(device: Device): Result<Boolean> =
        apiCall { api.scan("${device.baseUrl}/api/scan").queued }

    /** JPEG único da câmera — vira amostra no cadastro de pessoas. */
    suspend fun capture(device: Device): Result<ByteArray> =
        apiCall(retries = 0) { api.capture(device.captureUrl).use { it.bytes() } }

    /**
     * Testa a conexão antes de salvar: um IP digitado errado não vira um card
     * permanentemente offline na lista.
     */
    suspend fun addDevice(name: String, host: String, port: Int, streamPort: Int): Result<Device> {
        val probe = Device(id = "probe", name = name, host = host, port = port, streamPort = streamPort)
        return status(probe).map { settings.addDevice(name, host, port, streamPort) }
    }

    /**
     * Salva a edição de um dispositivo.
     *
     * A conexão só é testada quando o endereço muda: renomear ou girar a
     * imagem não deve falhar por causa de uma placa temporariamente offline.
     */
    suspend fun updateDevice(original: Device, edited: Device): Result<Device> {
        if (edited.sameAddressAs(original)) {
            settings.updateDevice(edited)
            return Result.success(edited)
        }
        return status(edited).map {
            settings.updateDevice(edited)
            edited
        }
    }

    suspend fun removeDevice(deviceId: String) = settings.removeDevice(deviceId)

    suspend fun selectDevice(deviceId: String) = settings.selectDevice(deviceId)
}
