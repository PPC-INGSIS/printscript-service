package snippetsearcher.printscript.execution

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.json.JsonCompareMode
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import tools.jackson.databind.json.JsonMapper

// Con el service real: no tiene estado ni dependencias, y así se prueba la librería de punta a punta
@WebMvcTest(ExecutionController::class)
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
            jsonPath("$.message") { value("La versión '2.0' no existe. Versiones disponibles: 1.0, 1.1") }
        }
    }

    @Test
    fun `sin code en el body responde 400`() {
        post("""{"version":"1.0","inputs":[]}""").andExpect { status { isBadRequest() } }
    }

    @Test
    fun `sin inputs en el body responde 400`() {
        post("""{"code":"println(1);","version":"1.0"}""").andExpect { status { isBadRequest() } }
    }

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
