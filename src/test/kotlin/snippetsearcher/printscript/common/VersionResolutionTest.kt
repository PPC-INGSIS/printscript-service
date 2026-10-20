package snippetsearcher.printscript.common

import org.junit.jupiter.api.Test
import org.printscript.common.Version
import kotlin.test.assertEquals

class VersionResolutionTest {
    @Test
    fun `una versión que existe se resuelve`() {
        assertEquals(VersionResolution.Supported(Version.V11), resolveVersion("1.1"))
    }

    @Test
    fun `una versión que no existe trae el motivo`() {
        assertEquals(
            VersionResolution.Unsupported("La versión '2.0' no existe. Versiones disponibles: 1.0, 1.1"),
            resolveVersion("2.0"),
        )
    }
}
