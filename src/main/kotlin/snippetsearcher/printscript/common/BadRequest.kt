package snippetsearcher.printscript.common

import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity

fun badRequest(detail: String): ResponseEntity<ProblemDetail> {
    val problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail)
    // `message` está deprecado: se quita cuando snippet-service lea `detail`
    problem.setProperty("message", detail)
    return ResponseEntity.badRequest().body(problem)
}
