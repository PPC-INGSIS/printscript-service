package snippetsearcher.printscript.execution

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.web.socket.CloseStatus
import snippetsearcher.printscript.security.TestJwtDecoder
import java.net.http.WebSocketHandshakeException
import java.util.concurrent.ExecutionException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

private const val MAX_CODE_SIZE = 64 * 1024
private const val MAX_MESSAGE_SIZE = 256 * 1024

// Puerto real: MockMvc no hace el handshake de WebSocket
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestJwtDecoder::class)
class InteractiveExecutionHandlerTest {
    @LocalServerPort
    private var port = 0

    @Test
    fun `dos inputs respondidos de a uno llegan de punta a punta`() {
        val socket = startReadInput()

        socket.send("type" to "input", "value" to "Juan")
        assertEquals(output("Veces: "), socket.next())
        assertEquals(inputRequest, socket.next())
        socket.send("type" to "input", "value" to "3")
        assertEquals(output("Hola Juan"), socket.next())
        assertEquals(output("3"), socket.next())
        assertEquals(message("type" to "finished", "error" to null), socket.next())
        assertEquals(closed(CloseStatus.NORMAL), socket.next())
    }

    @Test
    fun `un error de ejecución termina con su mensaje y posición`() {
        val socket = TestSocket.open(port)

        socket.send("type" to "start", "code" to snippet("con-error-de-ejecucion.ps"), "version" to "1.0")

        assertEquals(output("antes"), socket.next())
        val finished = assertIs<Received.Message>(socket.next()).body
        assertEquals("finished", finished["type"].stringValue())
        assertEquals(2, finished["error"]["line"].intValue())
        assertEquals(9, finished["error"]["column"].intValue())
        assertEquals(closed(CloseStatus.NORMAL), socket.next())
    }

    @Test
    fun `un JSON inválido se rechaza con 1008`() {
        val socket = TestSocket.open(port)

        socket.sendText("no es json")

        assertRejected(socket, "El mensaje no es un JSON válido", CloseStatus.POLICY_VIOLATION)
    }

    @Test
    fun `un mensaje sin type se rechaza con 1008`() {
        val socket = TestSocket.open(port)

        socket.send("code" to "println(1);", "version" to "1.0")

        assertRejected(socket, "Falta el campo 'type'", CloseStatus.POLICY_VIOLATION)
    }

    @Test
    fun `un type desconocido se rechaza con 1008`() {
        val socket = TestSocket.open(port)

        socket.send("type" to "stop")

        assertRejected(
            socket,
            "Tipo de mensaje desconocido: 'stop'. Se espera 'start' o 'input'",
            CloseStatus.POLICY_VIOLATION,
        )
    }

    @Test
    fun `un start sin code se rechaza con 1008`() {
        val socket = TestSocket.open(port)

        socket.send("type" to "start", "version" to "1.1")

        assertRejected(socket, "El mensaje 'start' necesita el campo 'code' como texto", CloseStatus.POLICY_VIOLATION)
    }

    @Test
    fun `un input sin value se rechaza con 1008`() {
        val socket = startReadInput()

        socket.send("type" to "input")

        assertRejected(socket, "El mensaje 'input' necesita el campo 'value' como texto", CloseStatus.POLICY_VIOLATION)
    }

    @Test
    fun `un campo con otro tipo se rechaza como si faltara`() {
        val socket = TestSocket.open(port)

        socket.send("type" to "start", "code" to 5, "version" to "1.0")

        assertRejected(socket, "El mensaje 'start' necesita el campo 'code' como texto", CloseStatus.POLICY_VIOLATION)
    }

    @Test
    fun `un input antes de start se rechaza con 1008`() {
        val socket = TestSocket.open(port)

        socket.send("type" to "input", "value" to "Juan")

        assertRejected(socket, "El primer mensaje tiene que ser 'start'", CloseStatus.POLICY_VIOLATION)
    }

    @Test
    fun `un segundo start se rechaza con 1008`() {
        val socket = startReadInput()

        socket.send("type" to "start", "code" to snippet("10-hola-mundo.ps"), "version" to "1.0")

        assertRejected(socket, "La ejecución ya arrancó: no se puede mandar otro 'start'", CloseStatus.POLICY_VIOLATION)
    }

    @Test
    fun `una versión que no existe se rechaza con 1008 y el motivo`() {
        val socket = TestSocket.open(port)

        socket.send("type" to "start", "code" to snippet("10-hola-mundo.ps"), "version" to "2.0")

        val detail = "La versión '2.0' no existe. Versiones disponibles: 1.0, 1.1"
        assertRejected(socket, detail, CloseStatus.POLICY_VIOLATION)
    }

    @Test
    fun `un código de más de 64 KB se rechaza con 1009`() {
        val socket = TestSocket.open(port)

        socket.send("type" to "start", "code" to " ".repeat(MAX_CODE_SIZE + 1), "version" to "1.0")

        assertRejected(socket, "El código supera el tamaño máximo de 64 KB", CloseStatus.TOO_BIG_TO_PROCESS)
    }

    @Test
    fun `un mensaje de más de 256 KB se cierra con 1009 sin rejected`() {
        val socket = TestSocket.open(port)

        socket.send("type" to "start", "code" to " ".repeat(MAX_MESSAGE_SIZE), "version" to "1.0")

        assertEquals(closed(CloseStatus.TOO_BIG_TO_PROCESS), socket.next())
    }

    @Test
    fun `un handshake sin token responde 401`() {
        val failure = assertFailsWith<ExecutionException> { TestSocket.open(port, token = null) }

        assertEquals(401, assertIs<WebSocketHandshakeException>(failure.cause).response.statusCode())
    }

    private fun startReadInput(): TestSocket {
        val socket = TestSocket.open(port)
        socket.send("type" to "start", "code" to snippet("20-read-input.ps"), "version" to "1.1")
        assertEquals(output("Nombre: "), socket.next())
        assertEquals(inputRequest, socket.next())
        return socket
    }
}
