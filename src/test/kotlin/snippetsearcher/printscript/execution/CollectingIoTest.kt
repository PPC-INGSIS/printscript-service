package snippetsearcher.printscript.execution

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNull

private const val PREFIX = "Faltan inputs: el snippet pidió más de los que se enviaron"
private const val MAX_OUTPUT = 10L

class CollectingIoTest {
    @AfterEach
    fun clearInterrupt() {
        Thread.interrupted()
    }

    @Test
    fun `print acumula en orden`() {
        val io = CollectingIo(emptyList(), MAX_OUTPUT)

        io.print("uno")
        io.print("dos")

        assertEquals(listOf("uno", "dos"), io.outputs)
    }

    @Test
    fun `read devuelve los inputs en orden`() {
        val io = CollectingIo(listOf("Juan", "3"), MAX_OUTPUT)

        assertEquals("Juan", io.read("Nombre: "))
        assertEquals("3", io.read("Veces: "))
    }

    @Test
    fun `read sin inputs restantes falla diciendo cuántos se enviaron`() {
        assertEquals("$PREFIX (se envió 1)", missingInputMessage(1))
    }

    @Test
    fun `el mensaje de inputs faltantes se lee bien sin inputs`() {
        assertEquals("$PREFIX (no se envió ninguno)", missingInputMessage(0))
    }

    @Test
    fun `el mensaje de inputs faltantes se lee bien con varios inputs`() {
        assertEquals("$PREFIX (se enviaron 2)", missingInputMessage(2))
    }

    @Test
    fun `read no agrega nada a los outputs`() {
        val io = CollectingIo(listOf("Juan"), MAX_OUTPUT)

        io.read("Nombre: ")

        assertEquals(emptyList(), io.outputs)
    }

    @Test
    fun `env nunca expone variables del servidor`() {
        val io = CollectingIo(emptyList(), MAX_OUTPUT)

        assertNull(io.env("HOME"))
        assertNull(io.env("PATH"))
    }

    @Test
    fun `outputs es una copia y no cambia con prints posteriores`() {
        val io = CollectingIo(emptyList(), MAX_OUTPUT)
        io.print("uno")

        val snapshot = io.outputs
        io.print("dos")

        assertEquals(listOf("uno"), snapshot)
    }

    @Test
    fun `pasar el máximo de salida falla y conserva lo impreso hasta ahí`() {
        val io = CollectingIo(emptyList(), MAX_OUTPUT)
        io.print("12345")
        io.print("67890")

        val exception = assertThrows<OutputTooLargeException> { io.print("x") }

        assertEquals("La salida superó el máximo de 10 caracteres", exception.message)
        assertEquals(listOf("12345", "67890"), io.outputs)
    }

    @Test
    fun `con el thread interrumpido print falla con la cancelación y no guarda el texto`() {
        val io = CollectingIo(emptyList(), MAX_OUTPUT)
        Thread.currentThread().interrupt()

        assertThrows<ExecutionCancelled> { io.print("uno") }

        assertEquals(emptyList(), io.outputs)
    }

    @Test
    fun `con el thread interrumpido read falla con la cancelación y no consume el input`() {
        val io = CollectingIo(listOf("Juan"), MAX_OUTPUT)
        Thread.currentThread().interrupt()

        assertThrows<ExecutionCancelled> { io.read("Nombre: ") }

        assertEquals("Juan", io.read("Nombre: "))
    }

    private fun missingInputMessage(sent: Int): String? {
        val io = CollectingIo(List(sent) { "input" }, MAX_OUTPUT)
        repeat(sent) { io.read("") }

        return assertThrows<MissingInputException> { io.read("") }.message
    }
}
