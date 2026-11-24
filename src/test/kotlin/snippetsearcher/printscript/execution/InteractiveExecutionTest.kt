package snippetsearcher.printscript.execution

import org.junit.jupiter.api.Test
import org.printscript.common.Version
import snippetsearcher.printscript.common.SourceError
import snippetsearcher.printscript.execution.ExecutionEvent.Finished
import snippetsearcher.printscript.execution.ExecutionEvent.InputRequested
import snippetsearcher.printscript.execution.ExecutionEvent.Output
import java.time.Duration
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val MAX_OUTPUT = 1_000_000L
private const val MAX_PENDING_INPUTS = 10
private val defaultInputTimeout = Duration.ofSeconds(5)

// Lo bastante largo para no fallar en una máquina lenta: solo se espera entero si hay un bug
private const val EVENT_TIMEOUT_SECONDS = 5L
private const val QUIET_MILLIS = 200L

class InteractiveExecutionTest {
    private val events = LinkedBlockingQueue<ExecutionEvent>()

    @Test
    fun `dos inputs respondidos de a uno emiten cada output y cada pedido en orden`() {
        val execution = start("20-read-input.ps")

        assertEquals(Output("Nombre: "), next())
        assertEquals(InputRequested, next())
        assertTrue(execution.provideInput("Juan"))
        assertEquals(Output("Veces: "), next())
        assertEquals(InputRequested, next())
        assertTrue(execution.provideInput("3"))
        assertEquals(Output("Hola Juan"), next())
        assertEquals(Output("3"), next())
        assertEquals(Finished(null), next())
    }

    @Test
    fun `un output llega mientras la ejecución sigue abierta esperando un input`() {
        val execution = start("20-read-input.ps")

        assertEquals(Output("Nombre: "), next())
        assertEquals(InputRequested, next())
        assertNull(events.poll(QUIET_MILLIS, TimeUnit.MILLISECONDS))

        execution.cancel()
        assertIs<Finished>(next())
    }

    @Test
    fun `cancelar mientras espera un input termina con el error de cancelación`() {
        val execution = start("20-read-input.ps")
        assertEquals(Output("Nombre: "), next())
        assertEquals(InputRequested, next())

        execution.cancel()

        assertEquals(finished("La ejecución se canceló"), next())
    }

    @Test
    fun `cancelar antes de arrancar corta en el primer print`() {
        val execution = execution(snippet("10-hola-mundo.ps"), Version.V10)

        execution.cancel()
        execution.start()

        assertEquals(finished("La ejecución se canceló"), next())
    }

    @Test
    fun `si no llega un input a tiempo termina con el error de timeout`() {
        start("20-read-input.ps", inputTimeout = Duration.ofMillis(50))

        assertEquals(Output("Nombre: "), next())
        assertEquals(InputRequested, next())
        assertEquals(finished("No se recibió un input en 50 ms"), next())
    }

    @Test
    fun `los inputs que llegan antes de que el snippet los pida quedan encolados`() {
        val execution = execution(snippet("20-read-input.ps"))
        assertTrue(execution.provideInput("Juan"))
        assertTrue(execution.provideInput("3"))

        execution.start()

        val expected =
            listOf(
                Output("Nombre: "),
                InputRequested,
                Output("Veces: "),
                InputRequested,
                Output("Hola Juan"),
                Output("3"),
            )
        assertEquals(expected, List(expected.size) { next() })
        assertEquals(Finished(null), next())
    }

    @Test
    fun `con la cola de inputs llena rechaza el input`() {
        val execution = execution(snippet("20-read-input.ps"))
        repeat(MAX_PENDING_INPUTS) { assertTrue(execution.provideInput("input")) }

        assertFalse(execution.provideInput("de más"))
    }

    @Test
    fun `si la salida pasa el máximo termina con el error de salida`() {
        start("10-hola-mundo.ps", Version.V10, maxOutputSize = 5)

        assertEquals(finished("La salida superó el máximo de 5 caracteres"), next())
    }

    @Test
    fun `un error de sintaxis termina con su mensaje y posición`() {
        start("con-error-de-sintaxis.ps", Version.V10)

        val error = assertNotNull(assertIs<Finished>(next()).error)
        assertTrue(error.message.isNotBlank())
        assertEquals(1, error.line)
        assertEquals(17, error.column)
    }

    @Test
    fun `una excepción inesperada igual termina con Finished`() {
        val failingEmit: (ExecutionEvent) -> Unit = {
            if (it is Output) error("Falla del transporte")
            events.put(it)
        }

        execution(snippet("10-hola-mundo.ps"), Version.V10, emit = failingEmit).start()

        assertEquals(finished("Error inesperado al ejecutar el snippet"), next())
    }

    private fun start(
        name: String,
        version: Version = Version.V11,
        maxOutputSize: Long = MAX_OUTPUT,
        inputTimeout: Duration = defaultInputTimeout,
    ) = execution(snippet(name), version, maxOutputSize, inputTimeout).also { it.start() }

    private fun execution(
        code: String,
        version: Version = Version.V11,
        maxOutputSize: Long = MAX_OUTPUT,
        inputTimeout: Duration = defaultInputTimeout,
        emit: (ExecutionEvent) -> Unit = events::put,
    ) = InteractiveExecution(code, version, maxOutputSize, inputTimeout, MAX_PENDING_INPUTS, emit)

    private fun next(): ExecutionEvent =
        assertNotNull(events.poll(EVENT_TIMEOUT_SECONDS, TimeUnit.SECONDS), "No llegó el evento esperado")

    private fun finished(message: String) = Finished(SourceError(message, line = null, column = null))

    private fun snippet(name: String): String = javaClass.getResource("/snippets/$name")!!.readText()
}
