package com.example.espcamapp.data.repo

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** Erro já traduzido para uma mensagem que pode ir direto para a tela. */
class ApiException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Executa uma chamada de rede fora da main thread, com uma tentativa extra em
 * falha de I/O (backoff curto), e converte a exceção numa mensagem amigável.
 *
 * `CancellationException` é repassada para não engolir o cancelamento da corrotina.
 */
suspend fun <T> apiCall(
    retries: Int = 1,
    backoffMs: Long = 400,
    block: suspend () -> T
): Result<T> = withContext(Dispatchers.IO) {
    var lastError: Throwable? = null
    for (attempt in 0..retries) {
        try {
            return@withContext Result.success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            lastError = e
            // Só falhas de I/O merecem nova tentativa; erro HTTP se repetiria igual.
            if (e !is IOException || attempt == retries) break
            delay(backoffMs * (attempt + 1))
        }
    }
    val error = lastError ?: ApiException("Falha desconhecida")
    Result.failure(ApiException(friendlyMessage(error), error))
}

private fun friendlyMessage(e: Throwable): String = when (e) {
    is UnknownHostException -> "Endereço não encontrado na rede"
    is ConnectException -> "Não foi possível conectar (dispositivo offline?)"
    is SocketTimeoutException -> "Tempo esgotado ao conectar"
    is HttpException -> httpMessage(e.code())
    is IOException -> "Falha de rede: ${e.message ?: "sem detalhes"}"
    else -> e.message ?: "Erro inesperado"
}

private fun httpMessage(code: Int): String = when (code) {
    401 -> "Não autorizado (verifique o token de acesso)"
    404 -> "Recurso não encontrado no servidor"
    422 -> "Imagem recusada: é necessário exatamente um rosto"
    502 -> "O servidor não conseguiu falar com o ESP32-CAM"
    503 -> "Reconhecimento facial indisponível no servidor"
    else -> "Erro HTTP $code"
}
