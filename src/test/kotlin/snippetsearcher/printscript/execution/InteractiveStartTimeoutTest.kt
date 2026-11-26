package snippetsearcher.printscript.execution

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.web.socket.CloseStatus
import snippetsearcher.printscript.security.TestJwtDecoder
import kotlin.test.assertEquals
import kotlin.test.fail

private const val NO_START = "No llegó el 'start' en 500 ms"
private const val REJECTED_BY_SESSIONS =
    "Se alcanzó el máximo de conexiones abiertas a la vez (1). Probá de nuevo más tarde"

// Más largo que el plazo, para que el start de la ejecución que sigue corriendo ya haya pasado de largo
private const val LONGER_THAN_TIMEOUT_MILLIS = 1_000L
private const val ACCEPT_TIMEOUT_NANOS = 5_000_000_000L

// Contexto aparte: con un plazo de 500 ms, los tests de InteractiveExecutionHandlerTest que esperan se cortarían.
// max-sessions=1 para probar que la conexión rechazada devuelve su lugar.
@SpringBootTest(
    webEnvironment = WebEnvironment.RANDOM_PORT,
    properties = [
        "printscript.interactive.start-timeout=500ms",
        "printscript.interactive.max-sessions=1",
    ],
)
@Import(TestJwtDecoder::class)
class InteractiveStartTimeoutTest {
    @LocalServerPort
    private var port = 0

    @Test
    fun `una conexión que no manda el start se rechaza con 1008 al vencer el plazo`() {
        val socket = TestSocket.open(port)

        assertRejected(socket, NO_START, CloseStatus.POLICY_VIOLATION)
    }

    @Test
    fun `la conexión rechazada por el plazo libera su lugar`() {
        assertRejected(TestSocket.open(port), NO_START, CloseStatus.POLICY_VIOLATION)

        startAccepted().close()
    }

    // El plazo es solo para el start: una ejecución que arrancó a tiempo puede durar lo que quiera
    @Test
    fun `una ejecución que arrancó a tiempo sigue aunque dure más que el plazo`() {
        val socket = startAccepted()

        Thread.sleep(LONGER_THAN_TIMEOUT_MILLIS)

        socket.send("type" to "input", "value" to "Juan")
        assertEquals(output("Veces: "), socket.next())
        assertEquals(inputRequest, socket.next())
        socket.send("type" to "input", "value" to "3")
        assertEquals(output("Hola Juan"), socket.next())
        assertEquals(output("3"), socket.next())
        assertEquals(message("type" to "finished", "error" to null), socket.next())
        assertEquals(closed(CloseStatus.NORMAL), socket.next())
    }

    // El servidor descuenta la conexión después de mandar el cierre: al reconectar puede seguir contada un instante.
    // Se reintenta mientras la rechace por max-sessions; si el rechazo no liberara el lugar, se vence el plazo.
    private fun startAccepted(): TestSocket {
        val deadline = System.nanoTime() + ACCEPT_TIMEOUT_NANOS
        while (System.nanoTime() - deadline < 0) {
            val socket = TestSocket.open(port)
            // Si ya la rechazó, el envío puede fallar: lo que importa es el primer mensaje
            runCatching { socket.send("type" to "start", "code" to snippet("20-read-input.ps"), "version" to "1.1") }
            val first = socket.next()
            if (first != message("type" to "rejected", "detail" to REJECTED_BY_SESSIONS)) {
                assertEquals(output("Nombre: "), first)
                assertEquals(inputRequest, socket.next())
                return socket
            }
        }
        fail("La conexión nunca se aceptó: el lugar no se liberó")
    }
}
