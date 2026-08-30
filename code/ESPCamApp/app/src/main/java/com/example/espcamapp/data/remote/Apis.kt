package com.example.espcamapp.data.remote

import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Query
import retrofit2.http.Streaming
import retrofit2.http.Url

/**
 * Rotas REST do ESP32-CAM.
 *
 * Todas usam [Url] com a URL absoluta: o endereço de cada dispositivo é
 * cadastrado pelo usuário em tempo de execução, então não há uma base fixa.
 */
interface Esp32Api {

    @GET
    suspend fun status(@Url url: String): Esp32StatusDto

    /** `state`: 1 liga, 0 desliga. Sem `state` o firmware alterna. */
    @GET
    suspend fun flash(@Url url: String, @Query("state") state: Int): Esp32ActionDto

    @GET
    suspend fun relay(@Url url: String, @Query("state") state: Int): Esp32ActionDto

    /** Pulso de 3 s no relé de acesso. */
    @GET
    suspend fun unlock(@Url url: String): Esp32ActionDto

    /** Manda o próprio ESP32 capturar e postar em `FACE_SERVER_URL/detect`. */
    @GET
    suspend fun scan(@Url url: String): Esp32ScanDto

    /** JPEG único sob demanda — usado como amostra no cadastro de pessoas. */
    @Streaming
    @GET
    suspend fun capture(@Url url: String): ResponseBody
}

/**
 * Rotas do servidor Python de reconhecimento facial.
 *
 * Também usam [Url] absoluta, porque o endereço do servidor é configurável.
 */
interface FaceServerApi {

    @GET
    suspend fun health(@Url url: String): HealthDto

    @GET
    suspend fun people(@Url url: String): PeopleResponseDto

    @Multipart
    @POST
    suspend fun createPerson(
        @Url url: String,
        @Part("name") name: RequestBody,
        @Part("category") category: RequestBody,
        @Part("authorized") authorized: RequestBody,
        @Part("note") note: RequestBody,
        @Part files: List<MultipartBody.Part>
    ): PersonResponseDto

    @PATCH
    suspend fun patchPerson(@Url url: String, @Body patch: PersonPatchDto): PersonResponseDto

    @DELETE
    suspend fun deletePerson(@Url url: String)

    @GET
    suspend fun detections(@Url url: String, @Query("limit") limit: Int): DetectionsResponseDto

    @POST
    suspend fun scan(@Url url: String, @Body body: ScanRequestDto): DetectResponseDto

    @POST
    suspend fun enrollFromEsp32(@Url url: String, @Body body: EnrollRequestDto): PersonResponseDto
}
