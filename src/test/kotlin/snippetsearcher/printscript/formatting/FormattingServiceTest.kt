package snippetsearcher.printscript.formatting

import org.junit.jupiter.api.Test
import org.printscript.common.Version
import org.printscript.formatter.config.FormatterConfig
import org.printscript.formatter.config.Indent
import org.printscript.formatter.config.Spacing
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FormattingServiceTest {
    private val service = FormattingService()

    @Test
    fun `con la configuración por defecto devuelve el código tal cual`() {
        val code = snippet("30-para-formatear.ps")

        val response = service.format(code, Version.V11, FormatterConfig())

        assertEquals(code, response.formatted)
        assertNull(response.error)
    }

    @Test
    fun `aplica las reglas dadas`() {
        val config = FormatterConfig(spaceAroundAssignment = Spacing.SINGLE, indentInsideIf = Indent.of(4))

        val response = service.format(snippet("30-para-formatear.ps"), Version.V11, config)

        assertEquals("let x:number = 1;\nif (activo) {\n    println(x);\n}", response.formatted)
        assertNull(response.error)
    }

    @Test
    fun `un error en el código devuelve el error con su posición y nada formateado`() {
        val response = service.format(snippet("con-error-lexico.ps"), Version.V10, FormatterConfig())

        assertNull(response.formatted)
        val error = assertNotNull(response.error)
        assertTrue(error.message.isNotBlank())
        assertEquals(2, error.line)
        assertEquals(19, error.column)
    }

    private fun snippet(name: String): String = javaClass.getResource("/snippets/$name")!!.readText()
}
