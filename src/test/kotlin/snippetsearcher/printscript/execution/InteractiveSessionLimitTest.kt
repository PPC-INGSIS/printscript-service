package snippetsearcher.printscript.execution

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.web.socket.CloseStatus
import snippetsearcher.printscript.common.SourceError
import snippetsearcher.printscript.security.TestJwtDecoder
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.fail

private const val ACCEPT_TIMEOUT_NANOS = 5_000_000_000L
private const val EVENT_TIMEOUT_SECONDS = 5L

// Sin el arreglo, la ejecución se crea en milisegundos: con esto alcanza para verla
private const val QUIET_MILLIS = 200L
private const val REJECTED_ATTEMPTS = 5

// Unos 64 MB de salida (de ahí el max-output-size del contexto), más de lo que entra en los buffers de TCP de
// cualquier lado: el envío se tiene que trabar. Se arma acá porque son mil println iguales de un texto de 64 KB.
private val FLOODING_CODE =
    buildString {
        appendLine("let s0: string = \"xxxxxxxxxxxxxxxx\";")
        for (i in 1..12) appendLine("let s$i: string = s${i - 1} + s${i - 1};")
        repeat(1000) { appendLine("println(s12);") }
    }
private val CANCELLED = ExecutionEvent.Finished(SourceError("La ejecución se canceló", line = null, column = null))
private const val REJECTED_BY_SESSIONS =
    "Se alcanzó el máximo de conexiones abiertas a la vez (1). Probá de nuevo más tarde"

// Contexto aparte: con max-sessions=1 cualquier otra conexión abierta molestaría a los demás tests.
// El send-timeout es chico para que el corte del cliente que no lee se vea antes de los 5 s de espera.
@SpringBootTest(
    webEnvironment = WebEnvironment.RANDOM_PORT,
    properties = [
        "printscript.interactive.max-sessions=1",
        "printscript.interactive.send-timeout=500ms",
        "printscript.limits.max-output-size=128MB",
    ],
)
@Import(TestJwtDecoder::class, InteractiveSessionLimitTest.Recording::class)
class InteractiveSessionLimitTest {
    @LocalServerPort
    private var port = 0

    @Test
    fun `pasar max-sessions al conectar se rechaza con 1013`() {
        val first = startAccepted()

        val second = TestSocket.open(port)

        assertRejected(second, REJECTED_BY_SESSIONS, CloseStatus.SERVICE_OVERLOAD)
        first.close()
    }

    @Test
    fun `un start que llega detrás del rechazo no arranca una ejecución`() {
        val first = startAccepted()
        Recording.created.clear()

        // Varias veces: sin el arreglo, el start no siempre llega antes de que el servidor termine de cerrar
        repeat(REJECTED_ATTEMPTS) {
            val rejected = TestSocket.open(port)
            runCatching { startReadInput(rejected) }
            assertRejected(rejected, REJECTED_BY_SESSIONS, CloseStatus.SERVICE_OVERLOAD)
        }

        assertNull(Recording.created.poll(QUIET_MILLIS, TimeUnit.MILLISECONDS))
        first.close()
    }

    @Test
    fun `cerrar la conexión libera el lugar`() {
        startAccepted().close()

        startAccepted().close()
    }

    @Test
    fun `cerrar la conexión cancela la ejecución`() {
        val socket = startAccepted()
        val events = Recording.lastEvents.get()

        socket.close()

        assertEquals(CANCELLED, nextFinished(events))
    }

    @Test
    fun `un cliente que deja de leer pierde la conexión al vencer send-timeout`() {
        val stuck = TestSocket.open(port, reading = false)
        Recording.created.clear()
        stuck.send("type" to "start", "code" to FLOODING_CODE, "version" to "1.0")
        assertNotNull(Recording.created.poll(EVENT_TIMEOUT_SECONDS, TimeUnit.SECONDS), "No arrancó la ejecución")

        assertEquals(CANCELLED, nextFinished(Recording.lastEvents.get()))
        startAccepted().close()
        stuck.abort()
    }

    // El servidor descuenta la conexión después de mandar el cierre: al reconectar puede seguir contada un instante.
    // Se reintenta mientras la rechace por max-sessions; si cerrar no liberara el lugar, se vence el plazo.
    private fun startAccepted(): TestSocket {
        val deadline = System.nanoTime() + ACCEPT_TIMEOUT_NANOS
        while (System.nanoTime() - deadline < 0) {
            val socket = TestSocket.open(port)
            // Si ya la rechazó, el envío puede fallar: lo que importa es el primer mensaje
            runCatching { startReadInput(socket) }
            val first = socket.next()
            if (first != message("type" to "rejected", "detail" to REJECTED_BY_SESSIONS)) {
                assertEquals(output("Nombre: "), first)
                return socket
            }
            assertEquals(closed(CloseStatus.SERVICE_OVERLOAD), socket.next())
        }
        fail("Cerrar la conexión no liberó el lugar")
    }

    private fun startReadInput(socket: TestSocket) =
        socket.send("type" to "start", "code" to snippet("20-read-input.ps"), "version" to "1.1")

    private fun nextFinished(events: LinkedBlockingQueue<ExecutionEvent>): ExecutionEvent {
        while (true) {
            val event = assertNotNull(events.poll(EVENT_TIMEOUT_SECONDS, TimeUnit.SECONDS), "No terminó la ejecución")
            if (event is ExecutionEvent.Finished) return event
        }
    }

    // La ejecución real, avisando cada una que se crea y guardando los eventos de la última: después de cerrar,
    // el cliente ya no ve el Finished de la cancelación
    @TestConfiguration
    class Recording {
        @Bean
        @Primary
        fun recordingExecutions(default: DefaultInteractiveExecutionFactory) =
            InteractiveExecutionFactory { code, version, emit ->
                // Antes de avisar: quien espera en created lee lastEvents enseguida
                val events = LinkedBlockingQueue<ExecutionEvent>().also { lastEvents.set(it) }
                created.put(Unit)
                default.create(code, version) {
                    events.put(it)
                    emit(it)
                }
            }

        companion object {
            val created = LinkedBlockingQueue<Unit>()
            val lastEvents = AtomicReference<LinkedBlockingQueue<ExecutionEvent>>()
        }
    }
}
