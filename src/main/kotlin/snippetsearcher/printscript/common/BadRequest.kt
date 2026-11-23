package snippetsearcher.printscript.common

import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity

fun badRequest(detail: String): ResponseEntity<ProblemDetail> = problem(HttpStatus.BAD_REQUEST, detail)

fun badRequest(
    detail: String,
    rule: String,
): ResponseEntity<ProblemDetail> = badRequest(detail).also { it.body?.setProperty("rule", rule) }

// Spring 7 titula el 413 "Content Too Large" (RFC 9110); el contrato con Snippets dice "Payload Too Large"
fun payloadTooLarge(detail: String): ResponseEntity<ProblemDetail> =
    problem(HttpStatus.CONTENT_TOO_LARGE, detail).also { it.body?.title = "Payload Too Large" }

private fun problem(
    status: HttpStatus,
    detail: String,
): ResponseEntity<ProblemDetail> {
    val problem = ProblemDetail.forStatusAndDetail(status, detail)
    return ResponseEntity.status(status).body(problem)
}
