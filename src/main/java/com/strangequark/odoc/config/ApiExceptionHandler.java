package com.strangequark.odoc.config;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.server.ResponseStatusException;

/** RFC 9457-style error responses shared by all HTTP APIs. */
@RestControllerAdvice
class ApiExceptionHandler {
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ProblemDetail> responseStatus(
            ResponseStatusException exception, HttpServletRequest request) {
        HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
        return problem(status, exception.getReason(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> validation(
            MethodArgumentNotValidException exception, HttpServletRequest request) {
        ProblemDetail detail = baseProblem(HttpStatus.BAD_REQUEST, "Request validation failed.", request);
        detail.setProperty("errors", exception.getBindingResult().getFieldErrors().stream()
                .map(this::fieldError)
                .toList());
        return ResponseEntity.badRequest().body(detail);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ProblemDetail> unreadableRequest(
            HttpMessageNotReadableException exception, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "Request body must be valid JSON.", request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> unexpected(Exception exception, HttpServletRequest request) {
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.", request);
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String message, HttpServletRequest request) {
        return ResponseEntity.status(status).body(baseProblem(status, message, request));
    }

    private ProblemDetail baseProblem(HttpStatus status, String message, HttpServletRequest request) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(status, message == null ? status.getReasonPhrase() : message);
        detail.setType(URI.create("https://odoc.local/problems/" + status.value()));
        detail.setInstance(URI.create(request.getRequestURI()));
        detail.setProperty("requestId", request.getAttribute(RequestIdFilter.HEADER));
        return detail;
    }

    private ApiFieldError fieldError(FieldError error) {
        return new ApiFieldError(error.getField(), error.getDefaultMessage());
    }

    private record ApiFieldError(String field, String message) {}
}
