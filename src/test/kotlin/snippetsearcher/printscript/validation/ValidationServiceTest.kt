package snippetsearcher.printscript.validation

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.InputStream
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ValidationServiceTest {
    private val service = ValidationService()

    @Test
    fun `un snippet válido no tiene errores`() {
        val errors = service.validate("1.0", snippet("10-hola-mundo.ps"))

        assertTrue(errors.isEmpty())
    }

    @Test
    fun `un error de sintaxis trae su mensaje, línea y columna en base 1`() {
        val errors = service.validate("1.0", snippet("con-error-de-sintaxis.ps"))

        // let x: number = ;  →  el ; está en la línea 1, columna 17
        val error = errors.single()
        assertTrue(error.message.isNotBlank())
        assertEquals(1, error.line)
        assertEquals(17, error.column)
    }

    @Test
    fun `const no existe en la 1_0`() {
        val errors = service.validate("1.0", snippet("11-const-y-boolean.ps"))

        // const nombre: string  →  sin la palabra const, nombre queda en la línea 1, columna 7
        val first = errors.first()
        assertEquals(1, first.line)
        assertEquals(7, first.column)
    }

    @Test
    fun `const es válido en la 1_1`() {
        val errors = service.validate("1.1", snippet("11-const-y-boolean.ps"))

        assertTrue(errors.isEmpty())
    }

    @Test
    fun `una versión que no existe se rechaza`() {
        val exception = assertThrows<UnsupportedVersionException> { service.validate("2.0", "".byteInputStream()) }

        assertEquals("La versión '2.0' no existe. Versiones disponibles: 1.0, 1.1", exception.message)
    }

    private fun snippet(name: String): InputStream = javaClass.getResourceAsStream("/snippets/$name")!!
}
