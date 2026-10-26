package snippetsearcher.printscript.linting

import org.junit.jupiter.api.Test
import org.printscript.analyzer.config.AnalyzerConfig
import org.printscript.analyzer.config.CamelCase
import org.printscript.analyzer.config.SnakeCase
import org.printscript.common.Version
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LintingServiceTest {
    private val service = LintingService()

    @Test
    fun `con la configuración por defecto no hay violaciones`() {
        val response = service.lint(snippet("40-para-lintear.ps"), Version.V11, AnalyzerConfig())

        assertEquals(emptyList(), response.violations)
    }

    @Test
    fun `con camel case marca los identificadores en snake case`() {
        val config = AnalyzerConfig(namingConvention = CamelCase)

        val response = service.lint(snippet("40-para-lintear.ps"), Version.V11, config)

        assertEquals(
            listOf(
                LintViolation(
                    rule = "identifier-naming",
                    message = "El identificador 'mi_variable' no sigue la convención de nombres configurada.",
                    line = 1,
                    column = 5,
                    severity = "WARNING",
                ),
            ),
            response.violations,
        )
    }

    @Test
    fun `con snake case marca los identificadores en camel case`() {
        val config = AnalyzerConfig(namingConvention = SnakeCase)

        val response = service.lint(snippet("40-para-lintear.ps"), Version.V11, config)

        assertEquals(
            listOf(
                LintViolation(
                    rule = "identifier-naming",
                    message = "El identificador 'otraVariable' no sigue la convención de nombres configurada.",
                    line = 2,
                    column = 5,
                    severity = "WARNING",
                ),
            ),
            response.violations,
        )
    }

    @Test
    fun `marca un println con una expresión como argumento`() {
        val config = AnalyzerConfig(restrictPrintlnArguments = true)

        val response = service.lint(snippet("40-para-lintear.ps"), Version.V11, config)

        assertEquals(
            listOf(
                LintViolation(
                    rule = "println-argument",
                    message = "println solo admite un identificador o un literal, no una expresión.",
                    line = 3,
                    column = 9,
                    severity = "ERROR",
                ),
            ),
            response.violations,
        )
    }

    @Test
    fun `en 1_1 marca un readInput con una expresión como argumento`() {
        val config = AnalyzerConfig(restrictReadInputArguments = true)

        val response = service.lint(snippet("40-para-lintear.ps"), Version.V11, config)

        assertEquals(
            listOf(
                LintViolation(
                    rule = "read-input-argument",
                    message = "readInput solo admite un identificador o un literal, no una expresión.",
                    line = 4,
                    column = 32,
                    severity = "ERROR",
                ),
            ),
            response.violations,
        )
    }

    @Test
    fun `un error de sintaxis es una violación más y el análisis sigue con las sentencias siguientes`() {
        val config = AnalyzerConfig(namingConvention = CamelCase)

        val response = service.lint(snippet("con-error-de-sintaxis.ps"), Version.V10, config)

        assertEquals(listOf("syntax", "identifier-naming"), response.violations.map { it.rule })
        val syntax = response.violations.first()
        assertTrue(syntax.message.isNotBlank())
        assertEquals("ERROR", syntax.severity)
        assertEquals(1, syntax.line)
        assertEquals(2, response.violations.last().line)
    }

    private fun snippet(name: String): String = javaClass.getResource("/snippets/$name")!!.readText()
}
