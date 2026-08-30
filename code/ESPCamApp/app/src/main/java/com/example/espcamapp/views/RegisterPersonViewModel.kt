package com.example.espcamapp.views

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.espcamapp.appContainer
import com.example.espcamapp.data.model.Device
import com.example.espcamapp.data.repo.DeviceRepository
import com.example.espcamapp.data.repo.PeopleRepository
import com.example.espcamapp.data.repo.SampleImage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class RegisterPersonUiState(
    val name: String = "",
    val category: String = "Morador",
    val note: String = "",
    val authorized: Boolean = true,
    val samples: List<SampleImage> = emptyList(),
    val capturing: Boolean = false,
    val saving: Boolean = false,
    val message: String? = null,
    val warnings: List<String> = emptyList(),
    val savedPersonName: String? = null,
    val selectedDevice: Device? = null
) {
    val canSave: Boolean
        get() = name.isNotBlank() && samples.isNotEmpty() && !saving

    val sampleHint: String
        get() = when {
            samples.isEmpty() -> "Envie de 3 a 5 fotos nítidas com diferentes ângulos do rosto"
            samples.size < MIN_SAMPLES ->
                "${samples.size} de $MIN_SAMPLES amostras — abaixo disso a pessoa fica pendente"
            else -> "${samples.size} amostras prontas para o treinamento"
        }

    companion object {
        const val MIN_SAMPLES = 3
        const val MAX_SAMPLES = 5
    }
}

class RegisterPersonViewModel(
    private val peopleRepository: PeopleRepository,
    private val deviceRepository: DeviceRepository
) : ViewModel() {

    private val _state = MutableStateFlow(RegisterPersonUiState())
    val state: StateFlow<RegisterPersonUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(deviceRepository.devices, deviceRepository.selectedDeviceId) { devices, id ->
                devices.firstOrNull { it.id == id } ?: devices.firstOrNull()
            }.collect { device ->
                _state.value = _state.value.copy(selectedDevice = device)
            }
        }
    }

    fun setName(value: String) = update { it.copy(name = value) }
    fun setCategory(value: String) = update { it.copy(category = value) }
    fun setNote(value: String) = update { it.copy(note = value) }
    fun setAuthorized(value: Boolean) = update { it.copy(authorized = value) }

    /** Amostras escolhidas na galeria (já lidas para bytes pela tela). */
    fun addSamples(images: List<SampleImage>) {
        if (images.isEmpty()) return
        val room = RegisterPersonUiState.MAX_SAMPLES - _state.value.samples.size
        if (room <= 0) {
            showMessage("Máximo de ${RegisterPersonUiState.MAX_SAMPLES} amostras")
            return
        }
        update { it.copy(samples = it.samples + images.take(room)) }
        if (images.size > room) {
            showMessage("Só cabiam mais $room amostra(s); o excedente foi ignorado")
        }
    }

    fun removeSample(index: Int) {
        update { current ->
            current.copy(samples = current.samples.filterIndexed { i, _ -> i != index })
        }
    }

    /** "Capturar direto pelo ESP32-CAM": puxa `/capture` do dispositivo selecionado. */
    fun captureFromDevice() {
        val device = _state.value.selectedDevice
        if (device == null) {
            showMessage("Cadastre um ESP32-CAM na aba Dispositivos")
            return
        }
        if (_state.value.samples.size >= RegisterPersonUiState.MAX_SAMPLES) {
            showMessage("Máximo de ${RegisterPersonUiState.MAX_SAMPLES} amostras")
            return
        }
        viewModelScope.launch {
            update { it.copy(capturing = true) }
            deviceRepository.capture(device).fold(
                onSuccess = { bytes ->
                    val index = _state.value.samples.size + 1
                    update {
                        it.copy(
                            capturing = false,
                            samples = it.samples + SampleImage("esp32cam_$index.jpg", bytes),
                            message = "Amostra capturada em ${device.name}"
                        )
                    }
                },
                onFailure = { error ->
                    update {
                        it.copy(capturing = false, message = error.message ?: "Falha ao capturar")
                    }
                }
            )
        }
    }

    fun save() {
        val current = _state.value
        if (!current.canSave) {
            showMessage("Informe o nome e pelo menos uma amostra")
            return
        }
        viewModelScope.launch {
            update { it.copy(saving = true, warnings = emptyList()) }
            peopleRepository.create(
                name = current.name,
                category = current.category,
                authorized = current.authorized,
                note = current.note,
                samples = current.samples
            ).fold(
                onSuccess = { result ->
                    val person = result.person
                    update {
                        it.copy(
                            saving = false,
                            warnings = result.warnings,
                            savedPersonName = person?.name ?: current.name,
                            message = buildSavedMessage(person?.samples ?: 0, result.warnings.size)
                        )
                    }
                },
                onFailure = { error ->
                    update { it.copy(saving = false, message = error.message ?: "Falha ao cadastrar") }
                }
            )
        }
    }

    private fun buildSavedMessage(accepted: Int, rejected: Int): String {
        val base = "Cadastro salvo com $accepted amostra(s) — modelo retreinado"
        return if (rejected > 0) "$base. $rejected amostra(s) recusada(s)." else base
    }

    fun consumeMessage() = update { it.copy(message = null) }

    /** Limpa o formulário depois de sair da tela. */
    fun reset() {
        _state.value = RegisterPersonUiState(selectedDevice = _state.value.selectedDevice)
    }

    private fun showMessage(text: String) = update { it.copy(message = text) }

    private fun update(transform: (RegisterPersonUiState) -> RegisterPersonUiState) {
        _state.value = transform(_state.value)
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = appContainer
                RegisterPersonViewModel(container.peopleRepository, container.deviceRepository)
            }
        }
    }
}
