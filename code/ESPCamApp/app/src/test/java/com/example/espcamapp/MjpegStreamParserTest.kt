package com.example.espcamapp

import com.example.espcamapp.stream.MjpegStreamParser
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException

/** Testes do parser de `multipart/x-mixed-replace` servido pelo ESP32-CAM. */
class MjpegStreamParserTest {

    private val boundary = "123456789000000000000987654321"

    /** Monta um JPEG de mentira, mas com os marcadores reais SOI/EOI. */
    private fun fakeJpeg(payload: String): ByteArray =
        byteArrayOf(0xFF.toByte(), 0xD8.toByte()) +
            payload.toByteArray() +
            byteArrayOf(0xFF.toByte(), 0xD9.toByte())

    private fun stream(vararg frames: ByteArray, withContentLength: Boolean = true): MjpegStreamParser {
        val out = ByteArrayOutputStream()
        frames.forEach { frame ->
            out.write("--$boundary\r\n".toByteArray())
            out.write("Content-Type: image/jpeg\r\n".toByteArray())
            if (withContentLength) {
                out.write("Content-Length: ${frame.size}\r\n".toByteArray())
            }
            out.write("X-Timestamp: 12.345678\r\n".toByteArray())
            out.write("\r\n".toByteArray())
            out.write(frame)
            out.write("\r\n".toByteArray())
        }
        return MjpegStreamParser(ByteArrayInputStream(out.toByteArray()))
    }

    @Test
    fun `le quadros consecutivos usando Content-Length`() {
        val first = fakeJpeg("primeiro")
        val second = fakeJpeg("segundo")
        val parser = stream(first, second)

        assertArrayEquals(first, parser.nextFrame())
        assertArrayEquals(second, parser.nextFrame())
    }

    @Test
    fun `devolve null quando o stream termina`() {
        val parser = stream(fakeJpeg("unico"))
        parser.nextFrame()
        assertNull(parser.nextFrame())
    }

    @Test
    fun `cai para os marcadores SOI-EOI quando nao ha Content-Length`() {
        val frame = fakeJpeg("sem-content-length")
        val parser = stream(frame, withContentLength = false)
        assertArrayEquals(frame, parser.nextFrame())
    }

    @Test
    fun `ignora lixo antes do primeiro marcador no modo SOI-EOI`() {
        val frame = fakeJpeg("dados")
        val raw = ByteArrayOutputStream().apply {
            write("--$boundary\r\nContent-Type: image/jpeg\r\n\r\n".toByteArray())
            write("ruido-antes-do-jpeg".toByteArray())
            write(frame)
        }
        val parser = MjpegStreamParser(ByteArrayInputStream(raw.toByteArray()))
        assertArrayEquals(frame, parser.nextFrame())
    }

    @Test(expected = EOFException::class)
    fun `sinaliza quadro truncado`() {
        val raw = "--$boundary\r\nContent-Type: image/jpeg\r\nContent-Length: 100\r\n\r\nsó-alguns-bytes"
        MjpegStreamParser(ByteArrayInputStream(raw.toByteArray())).nextFrame()
    }

    @Test
    fun `bufferedBytes reflete o que ainda esta na fila`() {
        val first = fakeJpeg("a")
        val second = fakeJpeg("bbbbbbbbbb")
        val parser = stream(first, second)

        parser.nextFrame()
        // O segundo quadro (e seus cabeçalhos) já chegaram e continuam pendentes.
        assertEquals(true, parser.bufferedBytes() > 0)
    }
}
