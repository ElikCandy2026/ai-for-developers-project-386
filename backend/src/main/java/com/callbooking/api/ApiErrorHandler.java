package com.callbooking.api;

import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.callbooking.api.model.BadRequestCode;
import com.callbooking.api.model.BadRequestError;
import com.callbooking.api.model.ConflictCode;
import com.callbooking.api.model.ConflictError;
import com.callbooking.api.model.InternalError;
import com.callbooking.api.model.InternalErrorCode;
import com.callbooking.api.model.NotFoundCode;
import com.callbooking.api.model.NotFoundError;
import com.callbooking.application.ApplicationException;

import jakarta.validation.ConstraintViolationException;

/**
 * Turns every failure into the closed set of typed error responses. Bean
 * Validation failures and unreadable requests map to 400; framework errors such
 * as 405, 415 and 406 keep their own status and body from
 * {@link ResponseEntityExceptionHandler}; anything unexpected becomes
 * {@code INTERNAL_ERROR}.
 */
@RestControllerAdvice
public class ApiErrorHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiErrorHandler.class);

    private static final String VALIDATION_MESSAGE = "Проверьте корректность параметров запроса.";

    /** Human-readable Russian labels used in validation messages. */
    private static final Map<String, String> FIELD_LABELS = Map.of(
            "name", "Название",
            "description", "Описание",
            "durationMinutes", "Длительность",
            "meetingTypeId", "Тип встречи",
            "start", "Начало встречи",
            "guestName", "Имя гостя",
            "guestEmail", "Email гостя",
            "notes", "Комментарий");

    @ExceptionHandler(ApplicationException.class)
    public ResponseEntity<Object> handleApplicationException(ApplicationException exception) {
        return switch (exception.code()) {
            case VALIDATION_FAILED, MALFORMED_REQUEST, INVALID_DURATION -> ResponseEntity.badRequest()
                    .body(new BadRequestError(
                            BadRequestCode.fromValue(exception.code().name()), exception.getMessage()));
            case MEETING_TYPE_NOT_FOUND -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new NotFoundError(
                            NotFoundCode.fromValue(exception.code().name()), exception.getMessage()));
            case SLOT_UNAVAILABLE, SLOT_TAKEN -> ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ConflictError(
                            ConflictCode.fromValue(exception.code().name()), exception.getMessage()));
            case INTERNAL_ERROR -> internalError();
        };
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(this::describe)
                .distinct()
                .collect(Collectors.joining("; "));
        if (message.isEmpty()) {
            message = exception.getBindingResult().getGlobalErrors().stream()
                    .map(error -> error.getDefaultMessage())
                    .distinct()
                    .collect(Collectors.joining("; "));
        }
        return badRequest(message.isEmpty() ? VALIDATION_MESSAGE : message);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        return badRequest(VALIDATION_MESSAGE);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Object> handleConstraintViolation(ConstraintViolationException exception) {
        String message = exception.getConstraintViolations().stream()
                .map(violation -> label(violation.getPropertyPath().toString()) + ": " + violation.getMessage())
                .distinct()
                .collect(Collectors.joining("; "));
        return badRequest(message.isEmpty() ? VALIDATION_MESSAGE : message);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        return malformedRequest();
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(
            MissingServletRequestParameterException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        return malformedRequest();
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(
            TypeMismatchException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        return malformedRequest();
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpected(Exception exception) {
        log.error("Unexpected API error", exception);
        return internalError();
    }

    private ResponseEntity<Object> badRequest(String message) {
        return ResponseEntity.badRequest()
                .body(new BadRequestError(BadRequestCode.VALIDATION_FAILED, message));
    }

    private ResponseEntity<Object> malformedRequest() {
        return ResponseEntity.badRequest()
                .body(new BadRequestError(BadRequestCode.MALFORMED_REQUEST, "Не удалось разобрать запрос."));
    }

    private ResponseEntity<Object> internalError() {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new InternalError(InternalErrorCode.INTERNAL_ERROR, "Внутренняя ошибка сервера."));
    }

    private String describe(FieldError error) {
        return label(error.getField()) + ": " + error.getDefaultMessage();
    }

    private String label(String field) {
        return FIELD_LABELS.getOrDefault(field, field);
    }
}
