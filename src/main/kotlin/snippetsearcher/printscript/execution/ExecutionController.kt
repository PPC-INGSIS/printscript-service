package snippetsearcher.printscript.execution

import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import snippetsearcher.printscript.common.ApiError
import snippetsearcher.printscript.common.VersionResolution
import snippetsearcher.printscript.common.resolveVersion

@RestController
class ExecutionController(
    private val executionService: ExecutionService,
) {
    // Si falta un campo del body, Spring responde 400 antes de llegar acá
    @PostMapping("/execute", consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun execute(
        @RequestBody request: ExecutionRequest,
    ): ResponseEntity<*> =
        when (val resolution = resolveVersion(request.version)) {
            is VersionResolution.Unsupported -> ResponseEntity.badRequest().body(ApiError(resolution.message))
            is VersionResolution.Supported ->
                ResponseEntity.ok(executionService.execute(request.code, resolution.version, request.inputs))
        }
}
