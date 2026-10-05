package snippetsearcher.printscript.validation

import org.springframework.core.io.InputStreamResource
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class ValidationController(
    private val validationService: ValidationService,
) {
    // InputStreamResource y no String: el código llega como stream, sin copiarlo entero a memoria.
    // Si el body viene vacío, Spring responde 400 antes de llegar acá.
    @PostMapping("/validate", consumes = [MediaType.TEXT_PLAIN_VALUE])
    fun validate(
        @RequestParam version: String,
        @RequestBody source: InputStreamResource,
    ): ValidationResponse = ValidationResponse(validationService.validate(version, source.inputStream))
}
