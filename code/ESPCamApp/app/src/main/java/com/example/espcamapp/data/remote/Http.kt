package com.example.espcamapp.data.remote

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Fábricas de cliente HTTP.
 *
 * Dois clientes com perfis diferentes de timeout:
 *  - [restClient]: chamadas curtas (status, people, detections). Timeout baixo
 *    para que um dispositivo offline apareça como offline rápido.
 *  - [streamClient]: MJPEG e download de imagens. Sem timeout de leitura, já
 *    que o stream é uma resposta que nunca termina.
 */
object Http {

    private const val CONNECT_TIMEOUT_S = 4L
    private const val READ_TIMEOUT_S = 8L

    /**
     * Rotas que fazem visão computacional (cadastrar pessoa, escanear, enroll)
     * levam segundos por amostra no servidor Python. Com os 8 s do perfil curto
     * o app desistia e mostrava "Tempo esgotado" enquanto o servidor concluía
     * o cadastro — a pessoa era criada mas a tela acusava erro.
     */
    private const val HEAVY_READ_TIMEOUT_S = 90L

    /** Base exigida pelo Retrofit; ignorada porque toda rota usa `@Url` absoluta. */
    private const val PLACEHOLDER_BASE_URL = "http://localhost/"

    val json: Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    private val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }

    val restClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_S, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_S, TimeUnit.SECONDS)
            .writeTimeout(READ_TIMEOUT_S, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor(logging)
            .build()
    }

    val streamClient: OkHttpClient by lazy {
        restClient.newBuilder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()
    }

    /** Upload de amostras e reconhecimento: espera o servidor terminar. */
    private val heavyClient: OkHttpClient by lazy {
        restClient.newBuilder()
            .readTimeout(HEAVY_READ_TIMEOUT_S, TimeUnit.SECONDS)
            .writeTimeout(HEAVY_READ_TIMEOUT_S, TimeUnit.SECONDS)
            .build()
    }

    fun retrofit(client: OkHttpClient = restClient): Retrofit = Retrofit.Builder()
        .baseUrl(PLACEHOLDER_BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    fun esp32Api(): Esp32Api = retrofit().create(Esp32Api::class.java)

    /** Consultas rápidas: lista de pessoas, detecções, health. */
    fun faceServerApi(): FaceServerApi = retrofit().create(FaceServerApi::class.java)

    /** Mesmas rotas, cliente paciente — ver [HEAVY_READ_TIMEOUT_S]. */
    fun faceServerHeavyApi(): FaceServerApi =
        retrofit(heavyClient).create(FaceServerApi::class.java)
}
