package com.marketagri.transactions.infrastructure.http;

import java.time.Instant;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception exception,
            Object body,
            HttpHeaders headers,
            HttpStatusCode statusCode,
            WebRequest request) {
        HttpServletRequest servletRequest = ((ServletWebRequest) request).getRequest();
        HttpStatus standardStatus = HttpStatus.resolve(statusCode.value());
        String reason = standardStatus == null ? "HTTP error" : standardStatus.getReasonPhrase();
        String message = statusCode.is5xxServerError() ? "An unexpected error occurred" : reason;
        ApiError error = error(statusCode, message, servletRequest);
        return new ResponseEntity<>(error, headers, statusCode);
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(
            Exception exception, HttpServletRequest request) {
        return ResponseEntity.internalServerError()
                .body(error(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", request));
    }

    private ApiError error(HttpStatusCode status, String message, HttpServletRequest request) {
        HttpStatus standardStatus = HttpStatus.resolve(status.value());
        return new ApiError(
                Instant.now(),
                status.value(),
                standardStatus == null ? "HTTP error" : standardStatus.getReasonPhrase(),
                message,
                request.getRequestURI(),
                (String) request.getAttribute(CorrelationIdFilter.HEADER_NAME));
    }
}
