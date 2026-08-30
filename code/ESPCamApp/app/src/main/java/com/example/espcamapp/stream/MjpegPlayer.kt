package com.example.espcamapp.stream

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import com.example.espcamapp.data.remote.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.IOException
import kotlin.coroutines.coroutineContext

/** Estado do player exibido na tela Principal. */
sealed interface StreamState {
    /** Nenhum dispositivo selecionado. */
    data object Idle : StreamState

    data object Connecting : StreamState

    data object Live : StreamState

    data class Offline(val message: String) : StreamState
}

private const val RECONNECT_DELAY_MS = 2000L

/** A partir deste tanto de bytes já enfileirados, o quadro atual é descartado. */
private const val BACKLOG_SKIP_BYTES = 24 * 1024

/**
 * Player de MJPEG (`multipart/x-mixed-replace`) do ESP32-CAM.
 *
 * Consome o stream numa corrotina de I/O, decodifica cada quadro com
 * [BitmapFactory] e desenha o mais recente. Quadros atrasados são descartados
 * sem decodificar (ver [BACKLOG_SKIP_BYTES]) para o vídeo não acumular atraso,
 * e a conexão é refeita sozinha após uma falha de rede.
 *
 * @param streamUrl `http://<ip>:81/stream`, ou `null` quando não há dispositivo.
 * @param rotationDegrees giro de exibição (0/90/180/270) para câmeras montadas
 *   de lado ou de cabeça para baixo. É só apresentação: nada muda no firmware.
 */
@Composable
fun MjpegPlayer(
    streamUrl: String?,
    modifier: Modifier = Modifier,
    rotationDegrees: Int = 0,
    onStateChange: (StreamState) -> Unit = {},
    placeholder: @Composable BoxScope.() -> Unit = {}
) {
    var frame by remember(streamUrl) { mutableStateOf<Bitmap?>(null) }
    val currentOnStateChange by rememberUpdatedState(onStateChange)

    LaunchedEffect(streamUrl) {
        if (streamUrl.isNullOrBlank()) {
            currentOnStateChange(StreamState.Idle)
            return@LaunchedEffect
        }
        withContext(Dispatchers.IO) {
            while (coroutineContext.isActive) {
                currentOnStateChange(StreamState.Connecting)
                try {
                    readStream(streamUrl) { bitmap ->
                        frame = bitmap
                        currentOnStateChange(StreamState.Live)
                    }
                    // Stream terminou sem erro (dispositivo encerrou a resposta).
                    currentOnStateChange(StreamState.Offline("Transmissão encerrada"))
                } catch (e: IOException) {
                    coroutineContext.ensureActive()
                    currentOnStateChange(
                        StreamState.Offline(e.message ?: "Sem sinal do dispositivo")
                    )
                }
                delay(RECONNECT_DELAY_MS)
            }
        }
    }

    Box(modifier = modifier) {
        val bitmap = frame
        if (bitmap == null) {
            placeholder()
        } else {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Vídeo ao vivo do ESP32-CAM",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .rotatedFrame(rotationDegrees)
            )
        }
    }
}

/**
 * Gira o conteúdo em múltiplos de 90°.
 *
 * Em 90°/270° não basta o [graphicsLayer]: o quadro precisa ser medido com
 * largura e altura trocadas, senão sobra faixa preta nas laterais. O modificador
 * de layout mede o filho no quadro girado e reporta o tamanho original.
 */
private fun Modifier.rotatedFrame(degrees: Int): Modifier {
    val normalized = ((degrees % 360) + 360) % 360
    if (normalized == 0) return this
    val quarterTurn = normalized == 90 || normalized == 270
    val rotated = if (quarterTurn) {
        this.layout { measurable, constraints ->
            val swapped = Constraints(
                minWidth = constraints.minHeight,
                maxWidth = constraints.maxHeight,
                minHeight = constraints.minWidth,
                maxHeight = constraints.maxWidth
            )
            val placeable = measurable.measure(swapped)
            // Reporta o tamanho do quadro original e centraliza o filho girado.
            layout(placeable.height, placeable.width) {
                placeable.place(
                    x = (placeable.height - placeable.width) / 2,
                    y = (placeable.width - placeable.height) / 2
                )
            }
        }
    } else {
        this
    }
    return rotated.graphicsLayer { rotationZ = normalized.toFloat() }
}

/** Lê o stream até o fim ou até a corrotina ser cancelada. */
private suspend fun readStream(url: String, onFrame: (Bitmap) -> Unit) {
    val call = Http.streamClient.newCall(Request.Builder().url(url).build())
    // Cancelar a corrotina precisa fechar o socket: uma leitura bloqueada não
    // responde a cancelamento cooperativo.
    val cancelOnDispose = coroutineContext[kotlinx.coroutines.Job]
        ?.invokeOnCompletion { call.cancel() }

    try {
        call.execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Dispositivo respondeu HTTP ${response.code}")
            }
            val body = response.body ?: throw IOException("Resposta sem corpo")
            val parser = MjpegStreamParser(body.byteStream())

            while (coroutineContext.isActive) {
                val jpeg = parser.nextFrame() ?: return
                // Se já há outro quadro esperando, este está velho: descarta.
                if (parser.bufferedBytes() > BACKLOG_SKIP_BYTES) continue
                val bitmap = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size) ?: continue
                onFrame(bitmap)
            }
        }
    } finally {
        cancelOnDispose?.dispose()
    }
}
