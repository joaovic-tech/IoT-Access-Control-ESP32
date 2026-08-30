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

    /** Catálogo de categorias/vínculos oferecido na tela de cadastro. */
    @GET
    suspend fun categories(@Url url: String): CategoriesResponseDto

    /**
     * Cadastro completo: dados + amostras no mesmo POST. Os cômodos vão como
     * campo de formulário (ids separados por vírgula) porque um multipart não
     * tem corpo JSON onde caber a lista.
     */
    @Multipart
    @POST
    suspend fun createPerson(
        @Url url: String,
        @Part("name") name: RequestBody,
        @Part("category") category: RequestBody,
        @Part("rooms") rooms: RequestBody,
        @Part("authorized") authorized: RequestBody,
        @Part files: List<MultipartBody.Part>
    ): PersonResponseDto

    @PATCH
    suspend fun patchPerson(@Url url: String, @Body patch: PersonPatchDto): PersonResponseDto

    /** Amostras extras para quem já está cadastrado (usado na edição). */
    @Multipart
    @POST
    suspend fun addSamples(
        @Url url: String,
        @Part files: List<MultipartBody.Part>
    ): PersonResponseDto

    @DELETE
    suspend fun deletePerson(@Url url: String)

    @GET
    suspend fun detections(@Url url: String, @Query("limit") limit: Int): DetectionsResponseDto

    @POST
    suspend fun scan(@Url url: String, @Body body: ScanRequestDto): DetectResponseDto

    @POST
    suspend fun enrollFromEsp32(@Url url: String, @Body body: EnrollRequestDto): PersonResponseDto

    // --- Cômodos ---

    @GET
    suspend fun rooms(@Url url: String): RoomsResponseDto

    @POST
    suspend fun createRoom(@Url url: String, @Body body: RoomInDto): RoomResponseDto

    @PATCH
    suspend fun patchRoom(@Url url: String, @Body body: RoomInDto): RoomResponseDto

    @DELETE
    suspend fun deleteRoom(@Url url: String)

    // --- Assistente do administrador (G3) ---

    @POST
    suspend fun assistantAsk(
        @Url url: String,
        @Body body: AssistantQuestionDto
    ): AssistantResponseDto

    @POST
    suspend fun assistantApply(
        @Url url: String,
        @Body body: AssistantApplyDto
    ): AssistantResponseDto
}
