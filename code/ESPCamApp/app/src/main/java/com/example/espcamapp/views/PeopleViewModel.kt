package com.example.espcamapp.views

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.espcamapp.appContainer
import com.example.espcamapp.data.model.Device
import com.example.espcamapp.data.model.Person
import com.example.espcamapp.data.repo.DeviceRepository
import com.example.espcamapp.data.repo.PeopleRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class PeopleUiState(
    val people: List<Person> = emptyList(),
    val query: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val selectedDevice: Device? = null
) {
    /** Lista já filtrada pela busca da tela. */
    val visiblePeople: List<Person>
        get() = if (query.isBlank()) {
            people
        } else {
            people.filter { it.name.contains(query.trim(), ignoreCase = true) }
        }
}

class PeopleViewModel(
    private val peopleRepository: PeopleRepository,
    deviceRepository: DeviceRepository
) : ViewModel() {

    private val _state = MutableStateFlow(PeopleUiState())
    val state: StateFlow<PeopleUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(deviceRepository.devices, deviceRepository.selectedDeviceId) { devices, id ->
                devices.firstOrNull { it.id == id } ?: devices.firstOrNull()
            }.collect { device ->
                _state.value = _state.value.copy(selectedDevice = device)
            }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true)
            peopleRepository.list().fold(
                onSuccess = { list ->
                    _state.value = _state.value.copy(people = list, loading = false, error = null)
                },
                onFailure = { error ->
                    _state.value = _state.value.copy(loading = false, error = error.message)
                }
            )
        }
    }

    fun setQuery(query: String) {
        _state.value = _state.value.copy(query = query)
    }

    fun delete(person: Person) {
        viewModelScope.launch {
            val result = peopleRepository.delete(person.id)
            showMessage(
                result.fold(
                    { "${person.name} removido" },
                    { it.message ?: "Falha ao remover" }
                )
            )
            refresh()
        }
    }

    fun setAuthorized(person: Person, authorized: Boolean) {
        viewModelScope.launch {
            val result = peopleRepository.setAuthorized(person.id, authorized)
            showMessage(
                result.fold(
                    { if (authorized) "${person.name} autorizado" else "${person.name} sem autorização" },
                    { it.message ?: "Falha ao atualizar" }
                )
            )
            refresh()
        }
    }

    /** Adiciona uma amostra capturando direto no ESP32 (`POST /enroll/from-esp32`). */
    fun addSampleFromCamera(person: Person) {
        val device = _state.value.selectedDevice
        if (device == null) {
            showMessage("Cadastre um ESP32-CAM antes de capturar")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true)
            val result = peopleRepository.enrollFromEsp32(
                person.id,
                device.baseUrl,
                device.rotationDegrees
            )
            _state.value = _state.value.copy(loading = false)
            showMessage(
                result.fold(
                    { updated -> "Amostra adicionada (${updated?.samples ?: "?"} no total)" },
                    { it.message ?: "Falha ao capturar amostra" }
                )
            )
            refresh()
        }
    }

    fun consumeMessage() {
        _state.value = _state.value.copy(message = null)
    }

    private fun showMessage(text: String) {
        _state.value = _state.value.copy(message = text)
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = appContainer
                PeopleViewModel(container.peopleRepository, container.deviceRepository)
            }
        }
    }
}
