package snippetsearcher.printscript.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.Customizer.withDefaults
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain

/**
 * Quién puede entrar. Validar el token (firma, iss, aud, exp) lo configura application.properties;
 * acá se decide qué rutas lo exigen y con qué scope (presentación de Auth0, diap. 48).
 */
@Configuration
class SecurityConfiguration {
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .authorizeHttpRequests {
                it
                    // El healthcheck pega sin token: con 401 marcaría como caído a un contenedor sano
                    .requestMatchers("/actuator/health/**")
                    .permitAll()
                    // PrintScript no guarda ni modifica nada: todo es lectura. La escritura la chequea Snippets
                    .anyRequest()
                    .hasAuthority("SCOPE_read:snippets")
            }.oauth2ResourceServer { it.jwt(withDefaults()) }
            // Con Bearer no hay sesión: cada pedido trae su token y cualquier réplica lo valida
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            // CSRF abusa de las cookies que el browser manda solo; el header Authorization no se manda solo
            .csrf { it.disable() }
        return http.build()
    }
}
