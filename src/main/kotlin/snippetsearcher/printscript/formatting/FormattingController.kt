package snippetsearcher.printscript.formatting

import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import snippetsearcher.printscript.common.ApiError
import snippetsearcher.printscript.common.VersionResolution
import snippetsearcher.printscript.common.resolveVersion
import tools.jackson.databind.json.JsonMapper

@RestController
class FormattingController(
    private val formattingService: FormattingService,
    private val jsonMapper: JsonMapper,
) {
    // Si falta un campo del body, Spring responde 400 antes de llegar acá
    @PostMapping("/format", consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun format(
        @RequestBody request: FormattingRequest,
    ): ResponseEntity<*> =
        when (val version = resolveVersion(request.version)) {
            is VersionResolution.Unsupported -> ResponseEntity.badRequest().body(ApiError(version.message))
            is VersionResolution.Supported ->
                when (val rules = resolveRules(request.rules, jsonMapper)) {
                    is RulesResolution.Invalid -> ResponseEntity.badRequest().body(ApiError(rules.message))
                    is RulesResolution.Supported ->
                        ResponseEntity.ok(formattingService.format(request.code, version.version, rules.config))
                }
        }
}
