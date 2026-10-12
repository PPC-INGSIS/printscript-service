package snippetsearcher.printscript.execution

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.json.JsonCompareMode
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import snippetsearcher.printscript.source.InMemorySnippetReader

// Con el service real y el Snippet Store en memoria: así se prueba la librería de punta a punta
@WebMvcTest(ExecutionController::class)
@Import(ExecutionService::class, ExecutionControllerTest.Store::class)
class ExecutionControllerTest(
    @Autowired private val mockMvc: MockMvc,
) {
    @TestConfiguration
    class Store {
        @Bean
        fun snippetReader() =
            InMemorySnippetReader.fromResources("10-hola-mundo.ps", "20-read-input.ps", "con-error-de-ejecucion.ps")
    }

    @Test
    fun `un snippet con println responde 200 con lo impreso`() {
        execute("""{"path":"snippets/10-hola-mundo.ps","version":"1.0","inputs":[]}""").andExpect {
            status { isOk() }
            content { json("""{"outputs":["Joe Doe"],"error":null}""", JsonCompareMode.STRICT) }
        }
    }

    @Test
    fun `con inputs suficientes responde 200 con los prompts y lo impreso`() {
        execute("""{"path":"snippets/20-read-input.ps","version":"1.1","inputs":["Juan","3"]}""").andExpect {
            status { isOk() }
            jsonPath("$.outputs.length()") { value(4) }
            jsonPath("$.outputs[0]") { value("Nombre: ") }
            jsonPath("$.outputs[2]") { value("Hola Juan") }
        }
    }

    @Test
    fun `con inputs faltantes responde 200 con el error sin posición`() {
        execute("""{"path":"snippets/20-read-input.ps","version":"1.1","inputs":["Juan"]}""").andExpect {
            status { isOk() }
            content {
                json(
                    """
                    {
                      "outputs": ["Nombre: ", "Veces: "],
                      "error": {
                        "message": "Faltan inputs: el snippet pidió más de los 1 enviados",
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
        execute("""{"path":"snippets/con-error-de-ejecucion.ps","version":"1.0","inputs":[]}""").andExpect {
            status { isOk() }
            jsonPath("$.outputs.length()") { value(1) }
            jsonPath("$.outputs[0]") { value("antes") }
            jsonPath("$.error.line") { value(2) }
            jsonPath("$.error.column") { value(9) }
        }
    }

    @Test
    fun `una versión que no existe responde 400 con el motivo`() {
        execute("""{"path":"snippets/10-hola-mundo.ps","version":"2.0","inputs":[]}""").andExpect {
            status { isBadRequest() }
            jsonPath("$.message") { value("La versión '2.0' no existe. Versiones disponibles: 1.0, 1.1") }
        }
    }

    @Test
    fun `sin inputs en el body responde 400`() {
        execute("""{"path":"snippets/10-hola-mundo.ps","version":"1.0"}""").andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `una ruta que no existe responde 404 con el motivo`() {
        execute("""{"path":"snippets/no-existe","version":"1.1","inputs":[]}""").andExpect {
            status { isNotFound() }
            jsonPath("$.message") { value("No hay ningún snippet en la ruta 'snippets/no-existe'") }
        }
    }

    private fun execute(body: String) =
        mockMvc.post("/execute") {
            contentType = MediaType.APPLICATION_JSON
            content = body
        }
}
