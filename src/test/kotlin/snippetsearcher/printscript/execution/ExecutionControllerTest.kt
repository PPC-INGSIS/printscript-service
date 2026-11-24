package snippetsearcher.printscript.execution

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.json.JsonCompareMode
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import snippetsearcher.printscript.common.Limits
import tools.jackson.databind.json.JsonMapper

private const val MAX_CODE_SIZE = 64 * 1024

// Con el service real: solo depende de los límites y del executor, y así se prueba la librería de punta a punta
@WebMvcTest(ExecutionController::class)
// Sin los filtros de seguridad: este test prueba el controller. La seguridad está en SecurityConfigurationTest
@AutoConfigureMockMvc(addFilters = false)
@EnableConfigurationProperties(Limits::class)
@Import(ExecutionService::class)
class ExecutionControllerTest(
    @Autowired private val mockMvc: MockMvc,
) {
    private val json = JsonMapper()

    @Test
    fun `un snippet con println responde 200 con lo impreso`() {
        execute("10-hola-mundo.ps", "1.0").andExpect {
            status { isOk() }
            content { json("""{"outputs":["Joe Doe"],"error":null}""", JsonCompareMode.STRICT) }
        }
    }

    @Test
    fun `con inputs suficientes responde 200 con los prompts y lo impreso`() {
        execute("20-read-input.ps", "1.1", "Juan", "3").andExpect {
            status { isOk() }
            jsonPath("$.outputs.length()") { value(4) }
            jsonPath("$.outputs[0]") { value("Nombre: ") }
            jsonPath("$.outputs[2]") { value("Hola Juan") }
        }
    }

    @Test
    fun `con inputs faltantes responde 200 con el error sin posición`() {
        execute("20-read-input.ps", "1.1", "Juan").andExpect {
            status { isOk() }
            content {
                json(
                    """
                    {
                      "outputs": ["Nombre: ", "Veces: "],
                      "error": {
                        "message": "Faltan inputs: el snippet pidió más de los que se enviaron (se envió 1)",
                        "line": null,
                        "column": null
                      }
                    }
                    """,
                    JsonCompareMode.STRICT,
                )
            }
        }
    }

    @Test
    fun `un error de ejecución responde 200 con el error y lo impreso hasta ahí`() {
        execute("con-error-de-ejecucion.ps", "1.0").andExpect {
            status { isOk() }
            jsonPath("$.outputs.length()") { value(1) }
            jsonPath("$.outputs[0]") { value("antes") }
            jsonPath("$.error.line") { value(2) }
            jsonPath("$.error.column") { value(9) }
        }
    }

    @Test
    fun `una versión que no existe responde 400 con el motivo`() {
        execute("10-hola-mundo.ps", "2.0").andExpect {
            status { isBadRequest() }
            content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.detail") { value("La versión '2.0' no existe. Versiones disponibles: 1.0, 1.1") }
        }
    }

    @Test
    fun `sin code en el body responde 400`() {
        post("""{"version":"1.0","inputs":[]}""").andExpect { status { isBadRequest() } }
    }

    @Test
    fun `sin un campo del body responde 400 con los Problem Details de Spring`() {
        post("""{"version":"1.0","inputs":[]}""").andExpect {
            status { isBadRequest() }
            content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.status") { value(400) }
            jsonPath("$.title") { value("Bad Request") }
        }
    }

    @Test
    fun `sin inputs en el body responde 400`() {
        post("""{"code":"println(1);","version":"1.0"}""").andExpect { status { isBadRequest() } }
    }

    @Test
    fun `un code de más de 64 KB responde 413 con el motivo`() {
        withCode(" ".repeat(MAX_CODE_SIZE + 1)).andExpect {
            status { isEqualTo(413) }
            content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.title") { value("Payload Too Large") }
            jsonPath("$.detail") { value("El código supera el tamaño máximo de 64 KB") }
        }
    }

    @Test
    fun `un code de exactamente 64 KB no responde 413`() {
        withCode(" ".repeat(MAX_CODE_SIZE)).andExpect { status { isOk() } }
    }

    private fun withCode(code: String) =
        post(json.writeValueAsString(mapOf("code" to code, "version" to "1.0", "inputs" to emptyList<String>())))

    private fun execute(
        name: String,
        version: String,
        vararg inputs: String,
    ) = post(json.writeValueAsString(mapOf("code" to snippet(name), "version" to version, "inputs" to inputs.toList())))

    private fun post(body: String) =
        mockMvc.post("/execute") {
            contentType = MediaType.APPLICATION_JSON
            content = body
        }

    private fun snippet(name: String): String = javaClass.getResource("/snippets/$name")!!.readText()
}
