package snippetsearcher.printscript.security

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder

// Reemplaza al decoder de Auth0 en los tests con un puerto real: no sale a internet y todo token trae el scope
@TestConfiguration
class TestJwtDecoder {
    @Bean
    fun jwtDecoder() =
        JwtDecoder { token ->
            Jwt
                .withTokenValue(token)
                .header("alg", "none")
                .claim("scope", "read:snippets")
                .build()
        }
}
