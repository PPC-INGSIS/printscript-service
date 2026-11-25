package snippetsearcher.printscript.execution

import org.springframework.context.annotation.Configuration
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry

@Configuration
@EnableWebSocket
class WebSocketConfiguration(
    private val handler: InteractiveExecutionHandler,
) : WebSocketConfigurer {
    // Sin orígenes permitidos: se conecta Snippets, nunca el browser
    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        registry.addHandler(handler, "/execute/interactive")
    }
}
