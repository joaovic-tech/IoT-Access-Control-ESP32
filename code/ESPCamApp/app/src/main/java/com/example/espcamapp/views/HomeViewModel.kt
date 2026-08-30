package com.example.espcamapp.views

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.espcamapp.appContainer
import com.example.espcamapp.data.model.Detection
import com.example.espcamapp.data.model.Device
import com.example.espcamapp.data.repo.DetectionRepository
import com.example.espcamapp.data.repo.DeviceRepository
import com.example.espcamapp.data.settings.SettingsStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Estado da tela Principal. */
data class HomeUiState(
    val devices: List<Device> = emptyList(),
    val selectedDevice: Device? = null,
    val detections: List<Detection> = emptyList(),
    val loadingDetections: Boolean = false,
    val scanning: Boolean = false,
    val detectionsError: String? = null,
    /** Endereço do servidor de faces em uso — mostrado junto do erro. */
    val faceServerUrl: String = "",
    /** Mensagem efêmera (resultado do scan) para o snackbar. */
    val message: String? = null
) {
    val hasDevices: Boolean get() = devices.isNotEmpty()
}

class HomeViewModel(
    private val deviceRepository: DeviceRepository,
    private val detectionRepository: DetectionRepository,
    private val settings: SettingsStore
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(deviceRepository.devices, deviceRepository.selectedDeviceId) { devices, selectedId ->
                devices to devices.firstOrNull { it.id == selectedId }.orFirstOf(devices)
            }.collect { (devices, selected) ->
                _state.value = _state.value.copy(devices = devices, selectedDevice = selected)
            }
        }
        viewModelScope.launch {
            settings.faceServerUrl.collect { url ->
                _state.value = _state.value.copy(faceServerUrl = url)
            }
        }
        // Mantém "Rostos detectados recentes" atualizado sem ação do usuário.
        viewModelScope.launch {
            while (isActive) {
                refreshDetections()
                delay(REFRESH_INTERVAL_MS)
            }
        }
    }

    private fun Device?.orFirstOf(devices: List<Device>): Device? = this ?: devices.firstOrNull()

    fun selectDevice(device: Device) {
        viewModelScope.launch { deviceRepository.selectDevice(device.id) }
    }

    fun refreshDetections() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loadingDetections = true)
            detectionRepository.recent().fold(
                onSuccess = { list ->
                    _state.value = _state.value.copy(
                        detections = list,
                        loadingDetections = false,
                        detectionsError = null
                    )
                },
                onFailure = { error ->
                    _state.value = _state.value.copy(
                        loadingDetections = false,
                        detectionsError = error.message
                    )
                }
            )
        }
    }

    /** "Escanear ambiente": o servidor puxa um frame do ESP32 e reconhece. */
    fun scanEnvironment() {
        val device = _state.value.selectedDevice
        if (device == null) {
            _state.value = _state.value.copy(message = "Cadastre um ESP32-CAM antes de escanear")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(scanning = true)
            // Orientação e cômodo vão junto: o servidor gira o quadro antes de
            // detectar e confronta o cômodo com a regra de acesso da pessoa.
            val result =
                detectionRepository.scan(device.baseUrl, device.rotationDegrees, device.room)
            _state.value = _state.value.copy(
                scanning = false,
                message = result.fold({ it.message }, { it.message ?: "Falha ao escanear" })
            )
            refreshDetections()
        }
    }

    fun consumeMessage() {
        _state.value = _state.value.copy(message = null)
    }

    companion object {
        private const val REFRESH_INTERVAL_MS = 5_000L

        val Factory = viewModelFactory {
            initializer {
                val container = appContainer
                HomeViewModel(
                    container.deviceRepository,
                    container.detectionRepository,
                    container.settings
                )
            }
        }
    }
}
