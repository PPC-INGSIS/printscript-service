package snippetsearcher.printscript.common

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class ApiExceptionHandler {
    @ExceptionHandler(UnsupportedVersionException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun unsupportedVersion(exception: UnsupportedVersionException) = ApiError(exception.message.orEmpty())
}
