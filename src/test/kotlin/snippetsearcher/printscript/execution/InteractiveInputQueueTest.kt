package snippetsearcher.printscript.execution

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.web.socket.CloseStatus
import snippetsearcher.printscript.security.TestJwtDecoder
import kotlin.test.assertEquals

private const val MAX_PENDING_INPUTS = 32

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestJwtDecoder::class, InteractiveInputQueueTest.NeverFinishing::class)
class InteractiveInputQueueTest {
    @LocalServerPort
    private var port = 0

    @Test
    fun `con la cola de inputs llena se rechaza con 1008`() {
        val socket = TestSocket.open(port)
        socket.send("type" to "start", "code" to "println(\"hola\");", "version" to "1.0")
        assertEquals(output("hola"), socket.next())

        repeat(MAX_PENDING_INPUTS + 1) { socket.send("type" to "input", "value" to "de más") }

        assertRejected(
            socket,
            "Hay demasiados inputs sin consumir: el máximo es $MAX_PENDING_INPUTS",
            CloseStatus.POLICY_VIOLATION,
        )
    }

    // Un snippet solo espera dentro de readInput, y ahí consume la cola. Sin el Finished, la ejecución
    // termina sin leer y la conexión sigue abierta: los inputs se acumulan sin que nadie los consuma.
    @TestConfiguration
    class NeverFinishing {
        @Bean
        @Primary
        fun neverFinishingExecutions(default: DefaultInteractiveExecutionFactory) =
            InteractiveExecutionFactory { code, version, emit ->
                default.create(code, version) { if (it !is ExecutionEvent.Finished) emit(it) }
            }
    }
}
