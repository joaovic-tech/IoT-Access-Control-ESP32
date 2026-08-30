package com.example.espcamapp.views

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.espcamapp.appContainer
import com.example.espcamapp.data.model.Device
import com.example.espcamapp.data.model.DeviceStatus
import com.example.espcamapp.data.repo.DeviceRepository
import com.example.espcamapp.data.repo.PeopleRepository
import com.example.espcamapp.data.settings.SettingsStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Estado por dispositivo: online/offline + último status lido. */
data class DeviceCardState(
    val status: DeviceStatus? = null,
    val online: Boolean = false,
    val busy: Boolean = false
)

data class DevicesUiState(
    val devices: List<Device> = emptyList(),
    val cards: Map<String, DeviceCardState> = emptyMap(),
    val faceServerUrl: String = SettingsStore.DEFAULT_FACE_SERVER_URL,
    val saving: Boolean = false,
    /** Testando o endereço do servidor de faces (botão "Salvar endereço"). */
    val checkingServer: Boolean = false,
    /** Dispositivo aberto no formulário de edição, ou `null` com o diálogo fechado. */
    val editing: Device? = null,
    val message: String? = null
)

class DevicesViewModel(
    private val deviceRepository: DeviceRepository,
    private val peopleRepository: PeopleRepository,
    private val settings: SettingsStore
) : ViewModel() {

    private val _state = MutableStateFlow(DevicesUiState())
    val state: StateFlow<DevicesUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            deviceRepository.devices.collect { devices ->
                _state.value = _state.value.copy(devices = devices)
                refreshAll()
            }
        }
        viewModelScope.launch {
            settings.faceServerUrl.collect { url ->
                _state.value = _state.value.copy(faceServerUrl = url)
            }
        }
        viewModelScope.launch {
            while (isActive) {
                delay(STATUS_POLL_MS)
                refreshAll()
            }
        }
    }

    fun refreshAll() {
        _state.value.devices.forEach { device -> refresh(device) }
    }

    private fun refresh(device: Device) {
        viewModelScope.launch {
            deviceRepository.status(device).fold(
                onSuccess = { status -> updateCard(device.id) { it.copy(status = status, online = true) } },
                onFailure = { updateCard(device.id) { it.copy(online = false) } }
            )
        }
    }

    fun setFlash(device: Device, on: Boolean) {
        viewModelScope.launch {
            updateCard(device.id) { it.copy(busy = true) }
            deviceRepository.setFlash(device, on).fold(
                onSuccess = { flashOn ->
                    updateCard(device.id) { card ->
                        card.copy(busy = false, online = true, status = card.status?.copy(flashOn = flashOn))
                    }
                },
                onFailure = { error ->
                    updateCard(device.id) { it.copy(busy = false, online = false) }
                    showMessage(error.message ?: "Falha ao controlar o flash")
                }
            )
        }
    }

    /** Libera o acesso físico (pulso de 3 s no relé). Confirmado na UI antes de chegar aqui. */
    fun unlock(device: Device) {
        viewModelScope.launch {
            updateCard(device.id) { it.copy(busy = true) }
            val result = deviceRepository.unlock(device)
            updateCard(device.id) { it.copy(busy = false) }
            showMessage(
                result.fold(
                    { "Acesso liberado em ${device.name} por 3 s" },
                    { it.message ?: "Falha ao liberar o acesso" }
                )
            )
            refresh(device)
        }
    }

    /**
     * Valida os campos, testa a conexão e só então grava.
     * Devolve o erro pela mensagem da tela em vez de salvar um IP inválido.
     */
    fun addDevice(name: String, host: String, portText: String, streamPortText: String) {
        val fields = parseFields(name, host, portText, streamPortText) ?: return

        viewModelScope.launch {
            _state.value = _state.value.copy(saving = true)
            val result = deviceRepository.addDevice(
                fields.name,
                fields.host,
                fields.port,
                fields.streamPort
            )
            _state.value = _state.value.copy(saving = false)
            showMessage(
                result.fold(
                    { "${fields.name} conectado e salvo" },
                    { "Não foi possível conectar em ${fields.host}:${fields.port} — ${it.message}" }
                )
            )
        }
    }

    fun removeDevice(device: Device) {
        viewModelScope.launch {
            deviceRepository.removeDevice(device.id)
            showMessage("${device.name} removido")
        }
    }

    // ---------------------------------------------------------------- edição

    fun startEditing(device: Device) {
        _state.value = _state.value.copy(editing = device)
    }

    fun cancelEditing() {
        _state.value = _state.value.copy(editing = null)
    }

    /**
     * Grava a edição do dispositivo. O endereço só é testado quando muda
     * (ver [DeviceRepository.updateDevice]), então renomear ou girar a imagem
     * funciona mesmo com a placa offline.
     */
    fun saveEditing(
        name: String,
        host: String,
        portText: String,
        streamPortText: String,
        rotationDegrees: Int
    ) {
        val original = _state.value.editing ?: return
        val fields = parseFields(name, host, portText, streamPortText) ?: return
        val edited = original.copy(
            name = fields.name,
            host = fields.host,
            port = fields.port,
            streamPort = fields.streamPort,
            rotationDegrees = Device.normalizeRotation(rotationDegrees)
        )

        viewModelScope.launch {
            _state.value = _state.value.copy(saving = true)
            val result = deviceRepository.updateDevice(original, edited)
            _state.value = _state.value.copy(
                saving = false,
                editing = if (result.isSuccess) null else _state.value.editing
            )
            showMessage(
                result.fold(
                    { "${it.name} atualizado" },
                    { "Não foi possível conectar em ${fields.host}:${fields.port} — ${it.message}" }
                )
            )
            // Sem refreshAll() aqui: o collector de `devices` já dispara um ao
            // receber a lista nova. Chamar os dois fazia o sondar concorrente —
            // um no endereço antigo, outro no novo — e o resultado do antigo
            // podia chegar por último, marcando o card como offline.
        }
    }

    /** Giro rápido pelo card, sem abrir o formulário. */
    fun setRotation(device: Device, degrees: Int) {
        val rotated = device.copy(rotationDegrees = Device.normalizeRotation(degrees))
        viewModelScope.launch {
            deviceRepository.updateDevice(device, rotated)
            showMessage("Orientação: ${Device.rotationLabel(rotated.rotationDegrees)}")
        }
    }

    // ------------------------------------------------- servidor de faces

    /**
     * Testa `GET /health` antes de gravar o endereço do servidor Python.
     *
     * Sem esse teste um IP errado só aparecia mais tarde, como
     * "Não foi possível conectar" no cadastro de pessoas ou no scan — longe
     * do campo que causou o problema.
     */
    fun setFaceServerUrl(url: String) {
        val normalized = SettingsStore.normalizeUrl(url)
        viewModelScope.launch {
            _state.value = _state.value.copy(checkingServer = true)
            // Grava antes de checar: o repositório lê a URL do SettingsStore.
            settings.setFaceServerUrl(normalized)
            val health = peopleRepository.health()
            _state.value = _state.value.copy(checkingServer = false)
            showMessage(
                health.fold(
                    { info ->
                        if (info.engineAvailable) {
                            "Servidor OK em $normalized — ${info.knownPeople} pessoa(s) carregada(s)"
                        } else {
                            "Servidor respondeu, mas sem motor facial: ${info.engineError}"
                        }
                    },
                    { error ->
                        "Salvo, mas sem resposta de $normalized — ${error.message}. " +
                            "Confira o IP do PC (ipconfig) e a porta 8000 no firewall."
                    }
                )
            )
        }
    }

    /** Validação comum ao cadastro e à edição. */
    private data class DeviceFields(
        val name: String,
        val host: String,
        val port: Int,
        val streamPort: Int
    )

    private fun parseFields(
        name: String,
        host: String,
        portText: String,
        streamPortText: String
    ): DeviceFields? {
        val cleanName = name.trim()
        val cleanHost = host.trim()
        if (cleanName.isEmpty() || cleanHost.isEmpty()) {
            showMessage("Preencha o nome do ambiente e o endereço IP")
            return null
        }
        val port = portText.trim().toIntOrNull() ?: Device.DEFAULT_PORT
        val streamPort = streamPortText.trim().toIntOrNull() ?: Device.DEFAULT_STREAM_PORT
        if (port !in 1..65535 || streamPort !in 1..65535) {
            showMessage("Porta inválida")
            return null
        }
        return DeviceFields(cleanName, cleanHost, port, streamPort)
    }

    fun consumeMessage() {
        _state.value = _state.value.copy(message = null)
    }

    private fun showMessage(text: String) {
        _state.value = _state.value.copy(message = text)
    }

    private fun updateCard(deviceId: String, transform: (DeviceCardState) -> DeviceCardState) {
        val cards = _state.value.cards.toMutableMap()
        cards[deviceId] = transform(cards[deviceId] ?: DeviceCardState())
        _state.value = _state.value.copy(cards = cards)
    }

    companion object {
        private const val STATUS_POLL_MS = 8_000L

        val Factory = viewModelFactory {
            initializer {
                val container = appContainer
                DevicesViewModel(
                    container.deviceRepository,
                    container.peopleRepository,
                    container.settings
                )
            }
        }
    }
}
