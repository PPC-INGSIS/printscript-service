package snippetsearcher.printscript.common

import org.junit.jupiter.api.Test
import org.springframework.util.unit.DataSize
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class InteractiveLimitsTest {
    @Test
    fun `rechaza un máximo de inputs pendientes que no sea positivo`() {
        val error = assertFailsWith<IllegalArgumentException> { limits(maxPendingInputs = 0) }

        assertEquals("printscript.interactive.max-pending-inputs tiene que ser mayor a 0, pero es 0", error.message)
    }

    @Test
    fun `rechaza un timeout de input que no sea positivo`() {
        val error = assertFailsWith<IllegalArgumentException> { limits(inputTimeout = Duration.ZERO) }

        assertEquals("printscript.interactive.input-timeout tiene que ser mayor a 0, pero es 0 s", error.message)
    }

    @Test
    fun `rechaza un timeout de envío de menos de 1 ms`() {
        val error = assertFailsWith<IllegalArgumentException> { limits(sendTimeout = Duration.ofNanos(500_000)) }

        assertEquals("printscript.interactive.send-timeout tiene que ser de al menos 1 ms, pero es 0 ms", error.message)
    }

    @Test
    fun `rechaza un tamaño de mensaje que no entra en un Int`() {
        val error = assertFailsWith<IllegalArgumentException> { limits(maxMessageSize = DataSize.ofGigabytes(2)) }

        val expected =
            "printscript.interactive.max-message-size tiene que estar entre 1 byte y 2147483647 bytes, " +
                "pero es 2147483648 bytes"
        assertEquals(expected, error.message)
    }

    private fun limits(
        inputTimeout: Duration = Duration.ofSeconds(1),
        maxPendingInputs: Int = 1,
        maxMessageSize: DataSize = DataSize.ofKilobytes(1),
        sendTimeout: Duration = Duration.ofSeconds(1),
    ) = InteractiveLimits(
        inputTimeout,
        Duration.ofSeconds(1),
        maxSessions = 1,
        maxPendingInputs,
        maxMessageSize,
        sendTimeout,
    )
}
