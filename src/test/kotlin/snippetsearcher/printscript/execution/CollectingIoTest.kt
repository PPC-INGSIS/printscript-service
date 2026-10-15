package snippetsearcher.printscript.execution

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNull

private const val PREFIX = "Faltan inputs: el snippet pidió más de los que se enviaron"

class CollectingIoTest {
    @Test
    fun `print acumula en orden`() {
        val io = CollectingIo(emptyList())

        io.print("uno")
        io.print("dos")

        assertEquals(listOf("uno", "dos"), io.outputs)
    }

    @Test
    fun `read devuelve los inputs en orden`() {
        val io = CollectingIo(listOf("Juan", "3"))

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
        val io = CollectingIo(listOf("Juan"))

        io.read("Nombre: ")

        assertEquals(emptyList(), io.outputs)
    }

    @Test
    fun `env nunca expone variables del servidor`() {
        val io = CollectingIo(emptyList())

        assertNull(io.env("HOME"))
        assertNull(io.env("PATH"))
    }

    @Test
    fun `outputs es una copia y no cambia con prints posteriores`() {
        val io = CollectingIo(emptyList())
        io.print("uno")

        val snapshot = io.outputs
        io.print("dos")

        assertEquals(listOf("uno"), snapshot)
    }

    private fun missingInputMessage(sent: Int): String? {
        val io = CollectingIo(List(sent) { "input" })
        repeat(sent) { io.read("") }

        return assertThrows<MissingInputException> { io.read("") }.message
    }
}
