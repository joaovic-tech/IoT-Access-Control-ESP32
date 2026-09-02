package com.example.espcamapp.views

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.espcamapp.appContainer
import com.example.espcamapp.data.model.Device
import com.example.espcamapp.data.model.Room
import com.example.espcamapp.data.repo.AccessRepository
import com.example.espcamapp.data.repo.CreatePersonResult
import com.example.espcamapp.data.repo.DeviceRepository
import com.example.espcamapp.data.repo.PeopleRepository
import com.example.espcamapp.data.repo.RefusedSample
import com.example.espcamapp.data.repo.SampleImage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Estado da tela de cadastro/edição de pessoa.
 *
 * Os campos são os do design `cadastrar-nova-pessoa.png`: amostras, nome,
 * categoria/vínculo e os cômodos liberados.
 */
data class RegisterPersonUiState(
    /** `null` = cadastro novo; preenchido = edição de quem já existe. */
    val personId: String? = null,
    val name: String = "",
    val category: String = DEFAULT_CATEGORIES.first(),
    val categories: List<String> = DEFAULT_CATEGORIES,
    val rooms: List<Room> = emptyList(),
    val selectedRoomIds: Set<String> = emptySet(),
    /** Amostras já no servidor (só na edição), como URL da imagem. */
    val existingSampleUrls: List<String> = emptyList(),
    /** Amostras escolhidas agora, ainda não enviadas. */
    val samples: List<SampleImage> = emptyList(),
    val capturing: Boolean = false,
    val loading: Boolean = false,
    val saving: Boolean = false,
    val message: String? = null,
    /** Amostras que o servidor recusou no último envio, com o motivo de cada. */
    val refused: List<RefusedSample> = emptyList(),
    val savedPersonName: String? = null,
    val selectedDevice: Device? = null
) {
    val editing: Boolean get() = personId != null

    /** Nomes das fotos recusadas, para a grade marcar os slots correspondentes. */
    val refusedFileNames: Set<String> get() = refused.map { it.fileName }.toSet()

    /** Amostras que a grade mostra: as do servidor primeiro, depois as novas. */
    val totalSamples: Int get() = existingSampleUrls.size + samples.size

    /** Quantas ainda cabem no envio. */
    val freeSlots: Int get() = (MAX_SAMPLES - totalSamples).coerceAtLeast(0)

    /** Na edição o cadastro já tem amostras; no cadastro novo é preciso pelo menos uma. */
    val canSave: Boolean
        get() = name.isNotBlank() && (editing || samples.isNotEmpty()) && !saving

    val sampleHint: String
        get() = when {
            // Depois de um envio parcial, contar as recusadas junto daria a
            // impressão de que estão todas cadastradas.
            refused.isNotEmpty() ->
                "${existingSampleUrls.size} amostra(s) cadastrada(s) · " +
                    "${refused.size} recusada(s) abaixo"

            // O detector do dlib só enxerga rosto de frente: foto de perfil, de
            // cabeça baixa ou de queixo erguido é recusada na hora do cadastro.
            totalSamples == 0 ->
                "Envie de 3 a 5 fotos nítidas com o rosto DE FRENTE (pode variar " +
                    "expressão, luz e distância). Perfil ou cabeça muito inclinada " +
                    "não é aceito."
            totalSamples < MIN_SAMPLES ->
                "$totalSamples de $MIN_SAMPLES amostras — abaixo disso a pessoa fica pendente"
            else -> "$totalSamples amostras prontas para o treinamento"
        }

    companion object {
        const val MIN_SAMPLES = 3
        const val MAX_SAMPLES = 5

        /**
         * Usadas enquanto o catálogo do servidor não chega (ou se ele estiver
         * fora do ar): a tela precisa desenhar os chips do design mesmo assim.
         */
        val DEFAULT_CATEGORIES = listOf("Morador", "Visitante", "Prestador")
    }
}

class RegisterPersonViewModel(
    private val peopleRepository: PeopleRepository,
    private val deviceRepository: DeviceRepository,
    private val accessRepository: AccessRepository
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
        loadCatalogs()
    }

    /**
     * Abre a tela em branco (`personId` nulo) ou com o cadastro carregado.
     * Chamada pela tela sempre que o destino muda.
     */
    fun open(personId: String?) {
        if (_state.value.personId == personId && (personId == null || _state.value.name.isNotBlank())) {
            return
        }
        _state.value = RegisterPersonUiState(
            personId = personId,
            categories = _state.value.categories,
            rooms = _state.value.rooms,
            selectedDevice = _state.value.selectedDevice
        )
        loadCatalogs()
        if (personId != null) loadPerson(personId)
    }

    /** Categorias e cômodos: o vocabulário oferecido pelo formulário. */
    private fun loadCatalogs() {
        viewModelScope.launch {
            peopleRepository.categories().onSuccess { lista ->
                if (lista.isEmpty()) return@onSuccess
                update { estado ->
                    // Uma categoria antiga fora do catálogo continua visível,
                    // senão a edição a perderia sem o usuário perceber.
                    val extra = estado.category.takeIf { it.isNotBlank() && it !in lista }
                    estado.copy(categories = lista + listOfNotNull(extra))
                }
            }
        }
        viewModelScope.launch {
            accessRepository.rooms().onSuccess { comodos ->
                update { it.copy(rooms = comodos) }
            }
        }
    }

    private fun loadPerson(personId: String) {
        viewModelScope.launch {
            update { it.copy(loading = true) }
            val base = peopleRepository.serverBaseUrl()
            peopleRepository.find(personId).fold(
                onSuccess = { person ->
                    if (person == null) {
                        update { it.copy(loading = false, message = "Cadastro não encontrado") }
                        return@fold
                    }
                    update { estado ->
                        val categorias = if (
                            person.category.isNotBlank() && person.category !in estado.categories
                        ) {
                            estado.categories + person.category
                        } else {
                            estado.categories
                        }
                        estado.copy(
                            loading = false,
                            name = person.name,
                            category = person.category.ifBlank { estado.category },
                            categories = categorias,
                            selectedRoomIds = person.roomIds.toSet(),
                            existingSampleUrls = person.sampleUrls(base)
                        )
                    }
                },
                onFailure = { error ->
                    update { it.copy(loading = false, message = error.message ?: "Falha ao carregar") }
                }
            )
        }
    }

    fun setName(value: String) = update { it.copy(name = value) }

    fun setCategory(value: String) = update { it.copy(category = value) }

    /** Marca/desmarca um cômodo no cadastro desta pessoa. */
    fun toggleRoom(roomId: String) = update { estado ->
        val novos = if (roomId in estado.selectedRoomIds) {
            estado.selectedRoomIds - roomId
        } else {
            estado.selectedRoomIds + roomId
        }
        estado.copy(selectedRoomIds = novos)
    }

    /**
     * Amostras escolhidas na galeria (já lidas para bytes pela tela).
     * [ignoradas] são as fotos que a tela não conseguiu abrir — elas precisam
     * virar aviso, e não desaparecer em silêncio.
     */
    fun addSamples(images: List<SampleImage>, ignoradas: Int = 0) {
        if (images.isEmpty() && ignoradas == 0) return
        val livres = _state.value.freeSlots
        if (livres <= 0) {
            showMessage("Máximo de ${RegisterPersonUiState.MAX_SAMPLES} amostras")
            return
        }
        update { it.copy(samples = it.samples + images.take(livres)) }
        when {
            ignoradas > 0 ->
                showMessage("$ignoradas foto(s) não puderam ser lidas da galeria")
            images.size > livres ->
                showMessage("Só cabiam mais $livres amostra(s); o excedente foi ignorado")
        }
    }

    fun removeSample(index: Int) {
        update { current ->
            val removida = current.samples.getOrNull(index)
            current.copy(
                samples = current.samples.filterIndexed { i, _ -> i != index },
                // Tirar a foto da grade tira junto o aviso dela, senão o motivo
                // continuaria na tela apontando para uma foto que não existe mais.
                refused = current.refused.filterNot { it.fileName == removida?.fileName }
            )
        }
    }

    /** "Capturar direto pelo ESP32-CAM": puxa `/capture` do dispositivo selecionado. */
    fun captureFromDevice() {
        val device = _state.value.selectedDevice
        if (device == null) {
            showMessage("Cadastre um ESP32-CAM na aba Dispositivos")
            return
        }
        if (_state.value.freeSlots <= 0) {
            showMessage("Máximo de ${RegisterPersonUiState.MAX_SAMPLES} amostras")
            return
        }
        viewModelScope.launch {
            update { it.copy(capturing = true) }
            deviceRepository.capture(device).fold(
                onSuccess = { bytes ->
                    val index = _state.value.totalSamples + 1
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
            showMessage(
                if (current.editing) "Informe o nome"
                else "Informe o nome e pelo menos uma amostra"
            )
            return
        }
        viewModelScope.launch {
            update { it.copy(saving = true, refused = emptyList()) }
            if (current.personId == null) criar(current) else salvarEdicao(current)
        }
    }

    private suspend fun criar(current: RegisterPersonUiState) {
        peopleRepository.create(
            name = current.name,
            category = current.category,
            roomIds = current.selectedRoomIds.toList(),
            samples = current.samples
        ).fold(
            onSuccess = { result ->
                aplicarResultado(result, current.samples, current.name)
            },
            onFailure = { error ->
                update { it.copy(saving = false, message = error.message ?: "Falha ao cadastrar") }
            }
        )
    }

    /** Salva os campos e, só depois, envia as amostras novas (se houver). */
    private suspend fun salvarEdicao(current: RegisterPersonUiState) {
        val personId = current.personId ?: return
        val atualizada = peopleRepository.update(
            personId = personId,
            name = current.name,
            category = current.category,
            roomIds = current.selectedRoomIds.toList()
        )
        atualizada.onFailure { error ->
            update { it.copy(saving = false, message = error.message ?: "Falha ao salvar") }
            return
        }

        if (current.samples.isEmpty()) {
            update {
                it.copy(
                    saving = false,
                    savedPersonName = current.name,
                    message = "Cadastro de ${current.name} atualizado"
                )
            }
            return
        }

        peopleRepository.addSamples(personId, current.samples).fold(
            onSuccess = { result ->
                aplicarResultado(result, current.samples, current.name)
            },
            onFailure = { error ->
                // Os campos já foram salvos; só as amostras falharam.
                update {
                    it.copy(
                        saving = false,
                        savedPersonName = current.name,
                        message = "Cadastro salvo, mas as amostras falharam: " +
                            (error.message ?: "erro de rede")
                    )
                }
            }
        )
    }

    /**
     * Fecha o envio conforme o servidor aceitou ou não as amostras.
     *
     * Aceitou todas: sai da tela, como antes. Recusou alguma: a tela **fica
     * aberta** mostrando quais caíram e por quê — antes ela voltava para a
     * lista na mesma hora e a pessoa ficava com menos fotos do que escolheu,
     * sem nenhuma explicação. Como a pessoa já foi criada no servidor, a tela
     * passa a editá-la: salvar de novo manda só as fotos que faltam, em vez de
     * criar um cadastro repetido.
     */
    private suspend fun aplicarResultado(
        result: CreatePersonResult,
        enviadas: List<SampleImage>,
        nome: String
    ) {
        val person = result.person
        val aceitas = person?.samples ?: 0
        if (result.refused.isEmpty()) {
            update {
                it.copy(
                    saving = false,
                    refused = emptyList(),
                    savedPersonName = person?.name ?: nome,
                    message = "Cadastro salvo com $aceitas amostra(s) — modelo retreinado"
                )
            }
            return
        }

        val recusadas = result.refused.map { it.fileName }.toSet()
        val base = peopleRepository.serverBaseUrl()
        update { estado ->
            estado.copy(
                saving = false,
                personId = person?.id ?: estado.personId,
                existingSampleUrls = person?.sampleUrls(base) ?: estado.existingSampleUrls,
                samples = enviadas.filter { it.fileName in recusadas },
                refused = result.refused,
                message = "$aceitas foto(s) cadastrada(s); " +
                    "${result.refused.size} recusada(s) — veja o motivo na tela"
            )
        }
    }

    fun consumeMessage() = update { it.copy(message = null) }

    /** Limpa o formulário depois de sair da tela. */
    fun reset() {
        _state.value = RegisterPersonUiState(
            categories = _state.value.categories,
            rooms = _state.value.rooms,
            selectedDevice = _state.value.selectedDevice
        )
    }

    private fun showMessage(text: String) = update { it.copy(message = text) }

    private fun update(transform: (RegisterPersonUiState) -> RegisterPersonUiState) {
        _state.value = transform(_state.value)
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = appContainer
                RegisterPersonViewModel(
                    container.peopleRepository,
                    container.deviceRepository,
                    container.accessRepository
                )
            }
        }
    }
}
