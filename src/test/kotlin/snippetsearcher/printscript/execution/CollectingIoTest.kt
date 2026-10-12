package snippetsearcher.printscript.execution

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
        val io = CollectingIo(listOf("Juan"))
        io.read("Nombre: ")

        val exception = assertThrows<MissingInputException> { io.read("Veces: ") }

        assertEquals("Faltan inputs: el snippet pidió más de los 1 enviados", exception.message)
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
}
