package com.example.espcamapp.stream

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream

/**
 * Parser de `multipart/x-mixed-replace` (MJPEG), o formato servido pelo
 * ESP32-CAM em `http://<ip>:81/stream`.
 *
 * Cada parte tem a forma:
 * ```
 * --<boundary>\r\n
 * Content-Type: image/jpeg\r\n
 * Content-Length: 12345\r\n
 * \r\n
 * <bytes JPEG>\r\n
 * ```
 *
 * Quando o `Content-Length` está presente (caso do firmware), os bytes são
 * lidos exatamente; senão, cai para a varredura dos marcadores SOI/EOI do
 * JPEG. A classe é pura (só depende de [InputStream]) para poder ser testada
 * sem Android.
 */
class MjpegStreamParser(input: InputStream) {

    private val stream = BufferedInputStream(input, BUFFER_SIZE)

    /** Bytes já recebidos e ainda não lidos — usado para descartar frames atrasados. */
    fun bufferedBytes(): Int = runCatching { stream.available() }.getOrDefault(0)

    /**
     * Devolve o próximo quadro JPEG, ou `null` quando o stream termina.
     *
     * @throws IOException em erro de leitura ou quadro maior que [MAX_FRAME_BYTES].
     */
    fun nextFrame(): ByteArray? {
        var contentLength = -1
        var sawHeader = false

        // 1. Consome boundary + cabeçalhos até a linha em branco que abre os dados.
        while (true) {
            val line = readLine() ?: return null
            if (line.isEmpty()) {
                if (sawHeader) break
                continue // linhas em branco entre partes
            }
            sawHeader = true
            if (line.startsWith(CONTENT_LENGTH, ignoreCase = true)) {
                contentLength = line.substringAfter(':').trim().toIntOrNull() ?: -1
            }
        }

        // 2. Corpo da parte.
        if (contentLength > MAX_FRAME_BYTES) {
            throw IOException("Quadro de $contentLength bytes acima do limite")
        }
        return if (contentLength > 0) readExactly(contentLength) else readUntilEndOfImage()
    }

    /** Lê uma linha terminada em `\n`, descartando o `\r`. `null` no fim do stream. */
    private fun readLine(): String? {
        val builder = StringBuilder()
        while (true) {
            val b = stream.read()
            if (b == -1) return if (builder.isEmpty()) null else builder.toString()
            if (b == '\n'.code) return builder.toString().trimEnd('\r')
            builder.append(b.toChar())
            if (builder.length > MAX_HEADER_LINE) {
                throw IOException("Cabeçalho MJPEG malformado (linha longa demais)")
            }
        }
    }

    private fun readExactly(size: Int): ByteArray {
        val data = ByteArray(size)
        var read = 0
        while (read < size) {
            val n = stream.read(data, read, size - read)
            if (n == -1) throw EOFException("Stream encerrado no meio de um quadro")
            read += n
        }
        return data
    }

    /** Fallback: procura SOI (FFD8) e devolve tudo até EOI (FFD9), inclusive. */
    private fun readUntilEndOfImage(): ByteArray? {
        val out = ByteArrayOutputStream(INITIAL_FRAME_SIZE)
        var previous = -1
        var started = false

        while (true) {
            val current = stream.read()
            if (current == -1) return null

            if (!started) {
                if (previous == MARKER && current == SOI) {
                    out.write(MARKER)
                    out.write(current)
                    started = true
                }
                previous = current
                continue
            }

            out.write(current)
            if (previous == MARKER && current == EOI) return out.toByteArray()
            if (out.size() > MAX_FRAME_BYTES) {
                throw IOException("Quadro sem marcador de fim acima do limite")
            }
            previous = current
        }
    }

    private companion object {
        const val CONTENT_LENGTH = "content-length"
        const val BUFFER_SIZE = 32 * 1024
        const val INITIAL_FRAME_SIZE = 48 * 1024
        const val MAX_FRAME_BYTES = 4 * 1024 * 1024
        const val MAX_HEADER_LINE = 512
        const val MARKER = 0xFF
        const val SOI = 0xD8
        const val EOI = 0xD9
    }
}
