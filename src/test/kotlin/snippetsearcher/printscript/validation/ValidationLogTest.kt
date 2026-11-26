package snippetsearcher.printscript.validation

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import snippetsearcher.printscript.common.Limits
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Cada validación deja un INFO con la versión y cuántos errores encontró, y nunca el código.
 *
 * Mismas anotaciones que ValidationControllerTest: Spring reusa ese contexto en vez de levantar otro.
 */
@WebMvcTest(ValidationController::class)
@AutoConfigureMockMvc(addFilters = false)
@EnableConfigurationProperties(Limits::class)
@Import(ValidationService::class)
class ValidationLogTest(
    @Autowired private val mockMvc: MockMvc,
) {
    @Test
    fun `un snippet valido deja un INFO con la version y cero errores`() {
        val logs = infosDuring { validate("1.0", "println(1);") }

        assertEquals(listOf("Validación con la versión 1.0, errores: 0"), logs)
    }

    @Test
    fun `un snippet con errores deja un INFO con cuantos encontro`() {
        val logs = infosDuring { validate("1.0", "let x: number = ;") }

        assertEquals(listOf("Validación con la versión 1.0, errores: 1"), logs)
    }

    // El código es del usuario y puede ser largo: no va al log
    @Test
    fun `el log no incluye el codigo del snippet`() {
        val logs = infosDuring { validate("1.0", "println(\"dato privado\");") }

        assertTrue(logs.none { "dato privado" in it }, "el log incluye el código: $logs")
    }

    // Un pedido rechazado no se validó: no hay resultado que contar
    @Test
    fun `una version que no existe no deja el INFO de validacion`() {
        val logs = infosDuring { validate("2.0", "println(1);") }

        assertTrue(logs.isEmpty())
    }

    private fun validate(
        version: String,
        code: String,
    ) = mockMvc.post("/validate?version=$version") {
        contentType = MediaType.TEXT_PLAIN
        content = code
    }

    // Los INFO del controller mientras corre el bloque, ya con los argumentos reemplazados
    private fun infosDuring(block: () -> Unit): List<String> {
        val logger = LoggerFactory.getLogger(ValidationController::class.java) as Logger
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        logger.addAppender(appender)
        try {
            block()
        } finally {
            logger.detachAppender(appender)
        }
        return appender.list.filter { it.level == Level.INFO }.map { it.formattedMessage }
    }
}
