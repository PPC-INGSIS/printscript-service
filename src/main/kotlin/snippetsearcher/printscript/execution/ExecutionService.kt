package snippetsearcher.printscript.execution

import org.printscript.common.Result
import org.printscript.common.Version
import org.printscript.lexer.source.StreamSourceReader
import org.printscript.runner.ExecuteRunner
import org.springframework.stereotype.Service
import snippetsearcher.printscript.common.SourceError
import snippetsearcher.printscript.common.resolveVersion
import snippetsearcher.printscript.common.toSourceError
import snippetsearcher.printscript.source.SnippetReader
import java.io.InputStream

@Service
class ExecutionService(
    private val snippetReader: SnippetReader,
) {
    fun execute(request: ExecutionRequest): ExecutionResponse {
        // Antes de abrir la fuente: una versión inválida no debería costar una lectura al store
        val version = resolveVersion(request.version)
        val io = CollectingIo(request.inputs)
        val error = snippetReader.open(request.path).use { run(version, io, it) }
        return ExecutionResponse(io.outputs, error)
    }

    private fun run(
        version: Version,
        io: CollectingIo,
        source: InputStream,
    ): SourceError? =
        try {
            val result = ExecuteRunner(version, io).execute { StreamSourceReader.of(source) }
            (result as? Result.Failure)?.error?.toSourceError()
        } catch (exception: MissingInputException) {
            // Sin posición: read no recibe el rango de la sentencia y ExecuteRunner no atrapa
            // la excepción, así que cuando llega acá ya no hay forma de saber dónde estaba
            SourceError(exception.message.orEmpty(), line = null, column = null)
        }
}
