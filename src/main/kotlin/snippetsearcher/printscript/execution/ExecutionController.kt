package snippetsearcher.printscript.execution

import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class ExecutionController(
    private val executionService: ExecutionService,
) {
    // Si falta un campo del body, Spring responde 400 antes de llegar acá
    @PostMapping("/execute", consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun execute(
        @RequestBody request: ExecutionRequest,
    ): ExecutionResponse = executionService.execute(request)
}
