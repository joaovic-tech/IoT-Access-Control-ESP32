package com.example.espcamapp.data.repo

import com.example.espcamapp.data.model.Person
import com.example.espcamapp.data.remote.EnrollRequestDto
import com.example.espcamapp.data.remote.FaceServerApi
import com.example.espcamapp.data.remote.PersonPatchDto
import com.example.espcamapp.data.remote.toDomain
import com.example.espcamapp.data.settings.SettingsStore
import kotlinx.coroutines.flow.first
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

/** Uma amostra facial já lida da galeria (bytes + nome de arquivo). */
data class SampleImage(val fileName: String, val bytes: ByteArray) {
    override fun equals(other: Any?): Boolean =
        this === other || (other is SampleImage && fileName == other.fileName && bytes.contentEquals(other.bytes))

    override fun hashCode(): Int = 31 * fileName.hashCode() + bytes.contentHashCode()
}

/** Resultado do cadastro: pessoa criada + avisos de amostras recusadas. */
data class CreatePersonResult(val person: Person?, val warnings: List<String>)

/** CRUD de pessoas no servidor Python de faces. */
class PeopleRepository(
    private val api: FaceServerApi,
    /** Cliente de timeout longo — codificar cada amostra leva segundos. */
    private val heavyApi: FaceServerApi,
    private val settings: SettingsStore
) {

    private suspend fun serverUrl(): String = settings.faceServerUrl.first()

    suspend fun list(): Result<List<Person>> = apiCall {
        val base = serverUrl()
        api.people("$base/people").people.map { it.toDomain(base) }
    }

    /** Uma pessoa pelo id — o servidor só expõe a lista, então filtra aqui. */
    suspend fun find(personId: String): Result<Person?> = apiCall {
        val base = serverUrl()
        api.people("$base/people").people
            .firstOrNull { it.id == personId }
            ?.toDomain(base)
    }

    /** Base do servidor de faces, para montar as URLs das amostras. */
    suspend fun serverBaseUrl(): String = serverUrl()

    /** Catálogo de categorias/vínculos; a tela cai no padrão se falhar. */
    suspend fun categories(): Result<List<String>> = apiCall {
        api.categories("${serverUrl()}/categories").categories
    }

    suspend fun create(
        name: String,
        category: String,
        roomIds: List<String>,
        samples: List<SampleImage>,
        authorized: Boolean = true
    ): Result<CreatePersonResult> = apiCall(retries = 0) {
        val base = serverUrl()
        val response = heavyApi.createPerson(
            url = "$base/people",
            name = name.asTextPart(),
            category = category.asTextPart(),
            rooms = roomIds.joinToString(",").asTextPart(),
            authorized = authorized.toString().asTextPart(),
            files = samples.toParts()
        )
        CreatePersonResult(response.person?.toDomain(base), response.warnings)
    }

    /** Edição do cadastro: nome, categoria e cômodos liberados. */
    suspend fun update(
        personId: String,
        name: String,
        category: String,
        roomIds: List<String>
    ): Result<Person?> = apiCall(retries = 0) {
        val base = serverUrl()
        api.patchPerson(
            "$base/people/$personId",
            PersonPatchDto(name = name, category = category, rooms = roomIds)
        ).person?.toDomain(base)
    }

    /** Amostras novas para quem já está cadastrado (`POST /people/{id}/samples`). */
    suspend fun addSamples(
        personId: String,
        samples: List<SampleImage>
    ): Result<CreatePersonResult> = apiCall(retries = 0) {
        val base = serverUrl()
        val response = heavyApi.addSamples(
            url = "$base/people/$personId/samples",
            files = samples.toParts()
        )
        CreatePersonResult(response.person?.toDomain(base), response.warnings)
    }

    suspend fun setAuthorized(personId: String, authorized: Boolean): Result<Person?> =
        apiCall(retries = 0) {
            val base = serverUrl()
            api.patchPerson(
                "$base/people/$personId",
                PersonPatchDto(authorized = authorized)
            ).person?.toDomain(base)
        }

    // Sem nova tentativa: se a primeira chegou ao servidor, repetir só faz a
    // tela esperar de novo pelo mesmo resultado.
    suspend fun delete(personId: String): Result<Unit> =
        apiCall(retries = 0) { api.deletePerson("${serverUrl()}/people/$personId") }

    /**
     * Pede ao servidor que capture no ESP32-CAM e use o frame como amostra.
     * `rotationDegrees` deixa a amostra na mesma orientação em que os rostos
     * serão reconhecidos depois.
     */
    suspend fun enrollFromEsp32(
        personId: String,
        esp32Url: String?,
        rotationDegrees: Int
    ): Result<Person?> = apiCall(retries = 0) {
        val base = serverUrl()
        heavyApi.enrollFromEsp32(
            "$base/enroll/from-esp32",
            EnrollRequestDto(
                personId = personId,
                esp32Url = esp32Url,
                rotation = rotationDegrees
            )
        ).person?.toDomain(base)
    }

    suspend fun health(): Result<com.example.espcamapp.data.model.ServerHealth> =
        apiCall { api.health("${serverUrl()}/health").toDomain() }

    private companion object {
        val IMAGE_JPEG = "image/jpeg".toMediaType()
        val TEXT_PLAIN = "text/plain".toMediaType()

        fun String.asTextPart(): RequestBody = toRequestBody(TEXT_PLAIN)

        fun List<SampleImage>.toParts(): List<MultipartBody.Part> =
            mapIndexed { index, sample ->
                MultipartBody.Part.createFormData(
                    "files",
                    sample.fileName.ifBlank { "amostra_$index.jpg" },
                    sample.bytes.toRequestBody(IMAGE_JPEG)
                )
            }
    }
}
