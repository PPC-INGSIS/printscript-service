package snippetsearcher.printscript.common

import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity

// RFC 9745: desde cuándo están deprecadas las claves de la librería (2026-11-05)
private const val DEPRECATION = "@1793836800"

private val log = LoggerFactory.getLogger("snippetsearcher.printscript.common.Deprecation")

fun <T : Any> okWithDeprecation(
    endpoint: String,
    body: T,
    deprecatedKeys: List<String>,
): ResponseEntity<T> {
    if (deprecatedKeys.isEmpty()) return ResponseEntity.ok(body)
    log.warn("{} recibió claves deprecadas: {}", endpoint, deprecatedKeys)
    return ResponseEntity.ok().header("Deprecation", DEPRECATION).body(body)
}
