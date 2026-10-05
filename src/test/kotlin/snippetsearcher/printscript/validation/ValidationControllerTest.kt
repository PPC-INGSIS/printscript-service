package snippetsearcher.printscript.validation

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post

// Con el service real: no tiene estado ni dependencias, y así se prueba la librería de punta a punta
@WebMvcTest(ValidationController::class)
@Import(ValidationService::class)
class ValidationControllerTest(
    @Autowired private val mockMvc: MockMvc,
) {
    @Test
    fun `un snippet válido responde 200 sin errores`() {
        validate("1.0", snippet("10-hola-mundo.ps")).andExpect {
            status { isOk() }
            jsonPath("$.errors.length()") { value(0) }
        }
    }

    @Test
    fun `un snippet inválido responde 200 con el error y sus campos`() {
        validate("1.0", snippet("con-error-de-sintaxis.ps")).andExpect {
            status { isOk() }
            jsonPath("$.errors.length()") { value(1) }
            jsonPath("$.errors[0].message") { value("Se esperaba un valor, un identificador o '('") }
            jsonPath("$.errors[0].line") { value(1) }
            jsonPath("$.errors[0].column") { value(17) }
        }
    }

    @Test
    fun `const con la versión 1_0 responde 200 con errores`() {
        validate("1.0", snippet("11-const-y-boolean.ps")).andExpect {
            status { isOk() }
            jsonPath("$.errors[0].line") { value(1) }
            jsonPath("$.errors[0].column") { value(7) }
        }
    }

    @Test
    fun `una versión que no existe responde 400 con el motivo`() {
        validate("2.0", snippet("10-hola-mundo.ps")).andExpect {
            status { isBadRequest() }
            jsonPath("$.message") { value("La versión '2.0' no existe. Versiones disponibles: 1.0, 1.1") }
        }
    }

    @Test
    fun `sin el parámetro version responde 400`() {
        mockMvc
            .post("/validate") {
                contentType = MediaType.TEXT_PLAIN
                content = snippet("10-hola-mundo.ps")
            }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `con el body vacío responde 400`() {
        validate("1.1", "").andExpect { status { isBadRequest() } }
    }

    private fun validate(
        version: String,
        code: String,
    ) = mockMvc.post("/validate") {
        param("version", version)
        contentType = MediaType.TEXT_PLAIN
        content = code
    }

    private fun snippet(name: String): String = javaClass.getResource("/snippets/$name")!!.readText()
}
