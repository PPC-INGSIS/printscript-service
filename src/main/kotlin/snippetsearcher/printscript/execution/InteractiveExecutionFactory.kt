package snippetsearcher.printscript.execution

import org.printscript.common.Version
import org.springframework.stereotype.Component
import snippetsearcher.printscript.common.InteractiveLimits
import snippetsearcher.printscript.common.Limits

// Separa crear la ejecución del handler: un test la reemplaza para llenar la cola de inputs sin depender de tiempos
fun interface InteractiveExecutionFactory {
    fun create(
        code: String,
        version: Version,
        emit: (ExecutionEvent) -> Unit,
    ): InteractiveExecution
}

@Component
class DefaultInteractiveExecutionFactory(
    private val limits: Limits,
    private val interactiveLimits: InteractiveLimits,
) : InteractiveExecutionFactory {
    override fun create(
        code: String,
        version: Version,
        emit: (ExecutionEvent) -> Unit,
    ) = InteractiveExecution(code, version, limits.maxOutputSize.toBytes(), interactiveLimits, emit)
}
