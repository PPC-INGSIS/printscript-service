package snippetsearcher.printscript.validation

import org.springframework.core.io.InputStreamResource
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import snippetsearcher.printscript.common.Limits
import snippetsearcher.printscript.common.VersionResolution
import snippetsearcher.printscript.common.badRequest
import snippetsearcher.printscript.common.payloadTooLarge
import snippetsearcher.printscript.common.resolveVersion

@RestController
class ValidationController(
    private val validationService: ValidationService,
    private val limits: Limits,
) {
    // InputStreamResource y no String: el código llega como stream, sin copiarlo entero a memoria.
    // Si el body viene vacío, Spring responde 400 antes de llegar acá.
    @PostMapping("/validate", consumes = [MediaType.TEXT_PLAIN_VALUE])
    fun validate(
        @RequestParam version: String,
        @RequestBody source: InputStreamResource,
    ): ResponseEntity<*> =
        when (val resolution = resolveVersion(version)) {
            is VersionResolution.Unsupported -> badRequest(resolution.message)
            is VersionResolution.Supported ->
                try {
                    val code = LimitedInputStream(source.inputStream, limits.maxCodeSize.toBytes())
                    val errors = validationService.validate(resolution.version, code)
                    ResponseEntity.ok(ValidationResponse(errors))
                } catch (expected: CodeTooLargeException) {
                    payloadTooLarge(limits.codeTooLargeMessage)
                }
        }
}
