package snippetsearcher.printscript.versions

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.json.JsonCompareMode
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@WebMvcTest(VersionsController::class)
class VersionsControllerTest(
    @Autowired private val mockMvc: MockMvc,
) {
    @Test
    fun `responde 200 con las versiones soportadas y la última`() {
        mockMvc.get("/versions").andExpect {
            status { isOk() }
            content { contentType(MediaType.APPLICATION_JSON) }
            content { json("""{"versions":[{"id":"1.0"},{"id":"1.1"}],"latest":"1.1"}""", JsonCompareMode.STRICT) }
        }
    }
}
