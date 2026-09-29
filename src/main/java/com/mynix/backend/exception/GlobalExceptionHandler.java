package com.mynix.backend.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(TooManyAttemptsException.class)
    public ResponseEntity<ApiError> handleTooManyAttempts(
            TooManyAttemptsException ex,
            HttpServletRequest request) {

        ApiError error = ApiError.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.TOO_MANY_REQUESTS.value())
                .error(HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase())
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(error);
    }

    /** Errors that carry their own status (e.g. 503 when uploads aren't set up). */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> handleStatus(
            ResponseStatusException ex,
            HttpServletRequest request) {

        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        ApiError error = ApiError.builder()
                .timestamp(LocalDateTime.now())
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(ex.getReason())
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.status(status).body(error);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleTooLarge(
            MaxUploadSizeExceededException ex,
            HttpServletRequest request) {

        ApiError error = ApiError.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.CONTENT_TOO_LARGE.value())
                .error(HttpStatus.CONTENT_TOO_LARGE.getReasonPhrase())
                .message("That file is too large. Photos can be up to 10 MB and videos up to 50 MB.")
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE).body(error);
    }

    /** Form validation: 400 with the first problem, e.g. "Keep the SEO title within 70 characters". */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {

        FieldError field = ex.getBindingResult().getFieldError();
        String message = field == null
                ? "Please check the form."
                : field.getField() + ": " + field.getDefaultMessage();

        ApiError error = ApiError.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .message(message)
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.badRequest().body(error);
    }

    /** Malformed JSON, wrong parameter types, missing parameters: no parser details in the reply. */
    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class,
            HttpMediaTypeNotSupportedException.class,
            ConstraintViolationException.class,
            HandlerMethodValidationException.class
    })
    public ResponseEntity<ApiError> handleBadRequest(
            Exception ex,
            HttpServletRequest request) {

        return error(HttpStatus.BAD_REQUEST, "The request isn't valid.", request);
    }

    /** Deliberate "not found" answers ("Address not found.") keep their message. */
    @ExceptionHandler(StoreNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(
            StoreNotFoundException ex,
            HttpServletRequest request) {

        return error(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    /** Database constraint problems: logged, never shown (they name tables and columns). */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiError> handleDataAccess(
            DataAccessException ex,
            HttpServletRequest request) {

        log.warn("Database error on {}: {}", request.getRequestURI(), ex.getMostSpecificCause().getMessage());
        return error(HttpStatus.CONFLICT, "That couldn't be saved. Please check the details and try again.", request);
    }

    /**
     * The POS's own messages ("Invalid username or password", "Insufficient
     * stock…") are plain RuntimeExceptions and are shown as written. Anything
     * else is unexpected: logged, with a generic reply.
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiError> handleRuntimeException(
            RuntimeException ex,
            HttpServletRequest request) {

        if (ex.getClass() == RuntimeException.class) {
            return error(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
        }

        log.error("Unexpected error on {}", request.getRequestURI(), ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong.", request);
    }

    private static ResponseEntity<ApiError> error(HttpStatus status, String message, HttpServletRequest request) {
        return ResponseEntity.status(status).body(ApiError.builder()
                .timestamp(LocalDateTime.now())
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(message)
                .path(request.getRequestURI())
                .build());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleException(
            Exception ex,
            HttpServletRequest request) {

        ApiError error = ApiError.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error(HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase())
                .message("Something went wrong.")
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(error);
    }
}