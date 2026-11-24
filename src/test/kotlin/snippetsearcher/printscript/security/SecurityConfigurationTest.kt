package snippetsearcher.printscript.security

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

// Con la aplicación entera: los tests de cada controller apagan los filtros, así que la seguridad se prueba acá
@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigurationTest(
    @Autowired private val mockMvc: MockMvc,
) {
    private val readScope = jwt().authorities(SimpleGrantedAuthority("SCOPE_read:snippets"))

    @Test
    fun `un pedido sin token responde 401`() {
        mockMvc.get("/versions").andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `un token sin el scope read_snippets responde 403`() {
        // jwt() sin authorities trae solo SCOPE_read: token válido, pero sin el permiso de la API
        mockMvc.get("/versions") { with(jwt()) }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `un token con el scope read_snippets entra`() {
        mockMvc.get("/versions") { with(readScope) }.andExpect { status { isOk() } }
    }

    @Test
    fun `un POST con el scope entra sin token de CSRF`() {
        mockMvc
            .post("/validate") {
                with(readScope)
                param("version", "1.1")
                contentType = MediaType.TEXT_PLAIN
                content = "println(1);"
            }.andExpect { status { isOk() } }
    }

    @Test
    fun `el healthcheck responde sin token`() {
        mockMvc.get("/actuator/health").andExpect { status { isOk() } }
    }
}
