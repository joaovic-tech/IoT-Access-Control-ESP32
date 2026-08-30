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
        api.people("${serverUrl()}/people").people.map { it.toDomain() }
    }

    suspend fun create(
        name: String,
        category: String,
        authorized: Boolean,
        note: String,
        samples: List<SampleImage>
    ): Result<CreatePersonResult> = apiCall(retries = 0) {
        val parts = samples.mapIndexed { index, sample ->
            MultipartBody.Part.createFormData(
                "files",
                sample.fileName.ifBlank { "amostra_$index.jpg" },
                sample.bytes.toRequestBody(IMAGE_JPEG)
            )
        }
        val response = heavyApi.createPerson(
            url = "${serverUrl()}/people",
            name = name.asTextPart(),
            category = category.asTextPart(),
            authorized = authorized.toString().asTextPart(),
            note = note.asTextPart(),
            files = parts
        )
        CreatePersonResult(response.person?.toDomain(), response.warnings)
    }

    suspend fun setAuthorized(personId: String, authorized: Boolean): Result<Person?> = apiCall {
        api.patchPerson(
            "${serverUrl()}/people/$personId",
            PersonPatchDto(authorized = authorized)
        ).person?.toDomain()
    }

    suspend fun delete(personId: String): Result<Unit> =
        apiCall { api.deletePerson("${serverUrl()}/people/$personId") }

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
        heavyApi.enrollFromEsp32(
            "${serverUrl()}/enroll/from-esp32",
            EnrollRequestDto(
                personId = personId,
                esp32Url = esp32Url,
                rotation = rotationDegrees
            )
        ).person?.toDomain()
    }

    suspend fun health(): Result<com.example.espcamapp.data.model.ServerHealth> =
        apiCall { api.health("${serverUrl()}/health").toDomain() }

    private companion object {
        val IMAGE_JPEG = "image/jpeg".toMediaType()
        val TEXT_PLAIN = "text/plain".toMediaType()

        fun String.asTextPart(): RequestBody = toRequestBody(TEXT_PLAIN)
    }
}
