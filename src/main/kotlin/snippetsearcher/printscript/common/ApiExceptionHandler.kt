package snippetsearcher.printscript.common

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice
import snippetsearcher.printscript.source.SnippetNotFoundException

@RestControllerAdvice
class ApiExceptionHandler {
    @ExceptionHandler(UnsupportedVersionException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun unsupportedVersion(exception: UnsupportedVersionException) = ApiError(exception.message.orEmpty())

    @ExceptionHandler(SnippetNotFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun snippetNotFound(exception: SnippetNotFoundException) = ApiError(exception.message.orEmpty())
}
