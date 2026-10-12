package snippetsearcher.printscript.execution

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import snippetsearcher.printscript.common.UnsupportedVersionException
import snippetsearcher.printscript.source.InMemorySnippetReader
import snippetsearcher.printscript.source.SnippetNotFoundException
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExecutionServiceTest {
    private val reader =
        InMemorySnippetReader.fromResources(
            "10-hola-mundo.ps",
            "20-read-input.ps",
            "con-error-de-sintaxis.ps",
            "con-error-de-ejecucion.ps",
            "con-read-env.ps",
        )
    private val service = ExecutionService(reader)

    @Test
    fun `devuelve lo que imprimió el snippet`() {
        val response = execute("10-hola-mundo.ps", "1.0")

        assertEquals(listOf("Joe Doe"), response.outputs)
        assertNull(response.error)
    }

    @Test
    fun `los prompts de readInput salen como outputs, antes de lo que imprime el snippet`() {
        val response = execute("20-read-input.ps", "1.1", "Juan", "3")

        assertEquals(listOf("Nombre: ", "Veces: ", "Hola Juan", "3"), response.outputs)
        assertNull(response.error)
    }

    @Test
    fun `con inputs de más ignora los que sobran`() {
        val response = execute("20-read-input.ps", "1.1", "Juan", "3", "sobra")

        assertNull(response.error)
    }

    @Test
    fun `si faltan inputs devuelve el error sin posición y lo impreso hasta ahí`() {
        val response = execute("20-read-input.ps", "1.1", "Juan")

        assertEquals(listOf("Nombre: ", "Veces: "), response.outputs)
        val error = assertNotNull(response.error)
        assertEquals("Faltan inputs: el snippet pidió más de los 1 enviados", error.message)
        assertNull(error.line)
        assertNull(error.column)
    }

    @Test
    fun `un error de ejecución trae su posición y lo impreso hasta ahí`() {
        val response = execute("con-error-de-ejecucion.ps", "1.0")

        assertEquals(listOf("antes"), response.outputs)
        val error = assertNotNull(response.error)
        assertTrue(error.message.isNotBlank())
        assertEquals(2, error.line)
        assertEquals(9, error.column)
    }

    @Test
    fun `un error de sintaxis se devuelve como error de la ejecución`() {
        val response = execute("con-error-de-sintaxis.ps", "1.0")

        val error = assertNotNull(response.error)
        assertEquals(1, error.line)
        assertEquals(17, error.column)
    }

    @Test
    fun `readEnv no lee las variables del servidor y corta la ejecución`() {
        val response = execute("con-read-env.ps", "1.1")

        assertEquals(listOf("antes"), response.outputs)
        val error = assertNotNull(response.error)
        assertEquals("La variable de entorno 'HOME' no está definida.", error.message)
        assertEquals(2, error.line)
    }

    @Test
    fun `cierra el stream aunque la ejecución termine con error`() {
        execute("20-read-input.ps", "1.1")
        execute("con-error-de-ejecucion.ps", "1.0")

        assertTrue(reader.allClosed)
    }

    @Test
    fun `una versión que no existe se rechaza sin abrir la fuente`() {
        assertThrows<UnsupportedVersionException> { execute("10-hola-mundo.ps", "2.0") }

        assertFalse(reader.allClosed)
    }

    @Test
    fun `una ruta que no existe se rechaza`() {
        val exception =
            assertThrows<SnippetNotFoundException> {
                service.execute(ExecutionRequest("snippets/no-existe", "1.1", emptyList()))
            }

        assertEquals("No hay ningún snippet en la ruta 'snippets/no-existe'", exception.message)
    }

    private fun execute(
        name: String,
        version: String,
        vararg inputs: String,
    ) = service.execute(ExecutionRequest("snippets/$name", version, inputs.toList()))
}
