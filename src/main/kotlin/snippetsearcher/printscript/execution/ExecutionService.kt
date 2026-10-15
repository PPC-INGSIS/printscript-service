package snippetsearcher.printscript.execution

import org.printscript.common.Result
import org.printscript.common.Version
import org.printscript.lexer.source.StringSourceReader
import org.printscript.runner.ExecuteRunner
import org.springframework.stereotype.Service
import snippetsearcher.printscript.common.SourceError
import snippetsearcher.printscript.common.toSourceError

@Service
class ExecutionService {
    fun execute(
        code: String,
        version: Version,
        inputs: List<String>,
    ): ExecutionResponse {
        val io = CollectingIo(inputs)
        val error = run(code, version, io)
        return ExecutionResponse(io.outputs, error)
    }

    private fun run(
        code: String,
        version: Version,
        io: CollectingIo,
    ): SourceError? =
        try {
            when (val result = ExecuteRunner(version, io).execute { StringSourceReader(code) }) {
                is Result.Success -> null
                is Result.Failure -> result.error.toSourceError()
            }
        } catch (exception: MissingInputException) {
            // Sin posición: read no recibe el rango de la sentencia y ExecuteRunner no atrapa
            // la excepción, así que cuando llega acá ya no hay forma de saber dónde estaba
            SourceError(exception.message.orEmpty(), line = null, column = null)
        }
}
