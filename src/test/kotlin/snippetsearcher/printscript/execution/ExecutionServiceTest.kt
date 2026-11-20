package snippetsearcher.printscript.execution

import org.junit.jupiter.api.Test
import org.printscript.common.Version
import org.springframework.core.task.SimpleAsyncTaskExecutor
import org.springframework.util.unit.DataSize
import snippetsearcher.printscript.common.Limits
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val STATEMENTS = 10_000

class ExecutionServiceTest {
    private val service = service(maxOutputSize = DataSize.ofMegabytes(1), timeout = Duration.ofSeconds(5))

    @Test
    fun `devuelve lo que imprimió el snippet`() {
        val response = execute("10-hola-mundo.ps", Version.V10)

        assertEquals(listOf("Joe Doe"), response.outputs)
        assertNull(response.error)
    }

    @Test
    fun `los prompts de readInput salen como outputs, antes de lo que imprime el snippet`() {
        val response = execute("20-read-input.ps", Version.V11, "Juan", "3")

        assertEquals(listOf("Nombre: ", "Veces: ", "Hola Juan", "3"), response.outputs)
        assertNull(response.error)
    }

    @Test
    fun `con inputs de más ignora los que sobran`() {
        val response = execute("20-read-input.ps", Version.V11, "Juan", "3", "sobra")

        assertNull(response.error)
    }

    @Test
    fun `si faltan inputs devuelve el error sin posición y lo impreso hasta ahí`() {
        val response = execute("20-read-input.ps", Version.V11, "Juan")

        assertEquals(listOf("Nombre: ", "Veces: "), response.outputs)
        val error = assertNotNull(response.error)
        assertEquals("Faltan inputs: el snippet pidió más de los que se enviaron (se envió 1)", error.message)
        assertNull(error.line)
        assertNull(error.column)
    }

    @Test
    fun `un error de ejecución trae su posición y lo impreso hasta ahí`() {
        val response = execute("con-error-de-ejecucion.ps", Version.V10)

        assertEquals(listOf("antes"), response.outputs)
        val error = assertNotNull(response.error)
        assertTrue(error.message.isNotBlank())
        assertEquals(2, error.line)
        assertEquals(9, error.column)
    }

    @Test
    fun `un error de sintaxis se devuelve como error de la ejecución`() {
        val response = execute("con-error-de-sintaxis.ps", Version.V10)

        val error = assertNotNull(response.error)
        assertEquals(1, error.line)
        assertEquals(17, error.column)
    }

    @Test
    fun `readEnv no lee las variables del servidor y corta la ejecución`() {
        val response = execute("con-read-env.ps", Version.V11)

        assertEquals(listOf("antes"), response.outputs)
        val error = assertNotNull(response.error)
        assertEquals("La variable de entorno 'HOME' no está definida.", error.message)
        assertEquals(2, error.line)
    }

    @Test
    fun `si la salida pasa el máximo devuelve el error sin posición y lo impreso hasta ahí`() {
        val service = service(maxOutputSize = DataSize.ofBytes(10), timeout = Duration.ofSeconds(5))

        val response = service.execute("println(12345);\nprintln(67890);\nprintln(1);", Version.V10, emptyList())

        assertEquals(listOf("12345", "67890"), response.outputs)
        val error = assertNotNull(response.error)
        assertEquals("La salida superó el máximo de 10 caracteres", error.message)
        assertNull(error.line)
        assertNull(error.column)
    }

    @Test
    fun `si la ejecución pasa el tiempo límite devuelve el error sin posición y lo impreso hasta ahí`() {
        val service = service(maxOutputSize = DataSize.ofMegabytes(1), timeout = Duration.ofMillis(100))

        val response = service.execute(statements(STATEMENTS), Version.V10, emptyList())

        assertEquals(listOf("antes"), response.outputs)
        val error = assertNotNull(response.error)
        assertEquals("La ejecución superó el tiempo límite de 100 ms", error.message)
        assertNull(error.line)
        assertNull(error.column)
    }

    private fun service(
        maxOutputSize: DataSize,
        timeout: Duration,
    ) = ExecutionService(Limits(DataSize.ofKilobytes(64), maxOutputSize, timeout), SimpleAsyncTaskExecutor())

    // No es la concatenación exponencial: esa agota el heap de los tests antes de pasar los 100 ms
    private fun statements(count: Int): String =
        (1..count).joinToString("\n", prefix = "println(\"antes\");\nlet x0: number = 0;\n") {
            "let x$it: number = x${it - 1} + 1;"
        }

    private fun execute(
        name: String,
        version: Version,
        vararg inputs: String,
    ) = service.execute(snippet(name), version, inputs.toList())

    private fun snippet(name: String): String = javaClass.getResource("/snippets/$name")!!.readText()
}
