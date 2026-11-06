package com.callbooking.api;

import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.callbooking.api.model.BadRequestError;
import com.callbooking.api.model.ConflictError;
import com.callbooking.api.model.NotFoundError;

import jakarta.validation.ConstraintViolationException;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Object> handleApiException(ApiException exception) {
        return switch (exception.status()) {
            case 400 -> ResponseEntity.badRequest()
                    .body(new BadRequestError(exception.code(), exception.getMessage()));
            case 404 -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new NotFoundError(exception.code(), exception.getMessage()));
            case 409 -> ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ConflictError(exception.code(), exception.getMessage()));
            default -> ResponseEntity.status(exception.status())
                    .body(Map.of("code", exception.code(), "message", exception.getMessage()));
        };
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<BadRequestError> handleValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(this::describe)
                .distinct()
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest().body(new BadRequestError("VALIDATION_FAILED", message));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<BadRequestError> handleConstraintViolation(ConstraintViolationException exception) {
        String message = exception.getConstraintViolations().stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest().body(new BadRequestError("VALIDATION_FAILED", message));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<BadRequestError> handleMethodValidation(HandlerMethodValidationException exception) {
        return ResponseEntity.badRequest()
                .body(new BadRequestError("VALIDATION_FAILED", "Проверьте корректность параметров запроса."));
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
    })
    public ResponseEntity<BadRequestError> handleMalformedRequest(Exception exception) {
        return ResponseEntity.badRequest()
                .body(new BadRequestError("MALFORMED_REQUEST", "Не удалось разобрать запрос."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleUnexpected(Exception exception) {
        log.error("Unexpected API error", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                        "code", "INTERNAL_ERROR",
                        "message", "Внутренняя ошибка сервера."));
    }

    private String describe(FieldError error) {
        return error.getField() + ": " + error.getDefaultMessage();
    }
}
