package edu.ashbrook.warden.common;

import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestValueException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Maps every failure to the contract's {@code ApiError}. Never leaks stack traces, SQL or class names. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String FIELDS = "Some fields need attention.";

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> invalidBody(MethodArgumentNotValidException ex) {
        Map<String, String> fields = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(e -> fields.putIfAbsent(e.getField(), e.getDefaultMessage()));
        return fieldErrors(fields);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiError> constraintViolation(ConstraintViolationException ex) {
        Map<String, String> fields = new LinkedHashMap<>();
        ex.getConstraintViolations().forEach(v -> {
            String path = v.getPropertyPath().toString();
            fields.putIfAbsent(path.substring(path.lastIndexOf('.') + 1), v.getMessage());
        });
        return fieldErrors(fields);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ApiError> invalidParameter(HandlerMethodValidationException ex) {
        Map<String, String> fields = new LinkedHashMap<>();
        ex.getParameterValidationResults().forEach(r -> r.getResolvableErrors().forEach(e ->
                fields.putIfAbsent(r.getMethodParameter().getParameterName(), e.getDefaultMessage())));
        return fieldErrors(fields);
    }

    @ExceptionHandler(FieldValidationException.class)
    ResponseEntity<ApiError> fieldValidation(FieldValidationException ex) {
        return fieldErrors(ex.getFieldErrors());
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingRequestValueException.class})
    ResponseEntity<ApiError> badParameter(Exception ex) {
        String name = ex instanceof MethodArgumentTypeMismatchException m ? m.getName() : "request";
        return fieldErrors(Map.of(name, "This value isn't valid."));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> unreadable() {
        return error(400, ErrorCode.VALIDATION_FAILED, "The request body couldn't be read.");
    }

    @ExceptionHandler(UnauthorizedException.class)
    ResponseEntity<ApiError> unauthorized(UnauthorizedException ex) {
        return error(401, ErrorCode.UNAUTHORIZED, ex.getMessage());
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ApiError> unauthenticated() {
        return error(401, ErrorCode.UNAUTHORIZED, "Sign in to continue.");
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiError> forbidden(AccessDeniedException ex) {
        String message = ex instanceof ForbiddenException ? ex.getMessage() : "You don't have permission to do that.";
        return error(403, ErrorCode.FORBIDDEN, message);
    }

    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<ApiError> notFound(NotFoundException ex) {
        return error(404, ErrorCode.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class,
            HttpRequestMethodNotSupportedException.class})
    ResponseEntity<ApiError> noRoute() {
        return error(404, ErrorCode.NOT_FOUND, "There's nothing at this address.");
    }

    @ExceptionHandler(ConflictException.class)
    ResponseEntity<ApiError> conflict(ConflictException ex) {
        return error(409, ErrorCode.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler({ObjectOptimisticLockingFailureException.class, PessimisticLockingFailureException.class})
    ResponseEntity<ApiError> concurrentChange() {
        return error(409, ErrorCode.CONFLICT, "Someone else changed this at the same time. Refresh and try again.");
    }

    @ExceptionHandler(BusinessRuleException.class)
    ResponseEntity<ApiError> businessRule(BusinessRuleException ex) {
        return error(422, ErrorCode.BUSINESS_RULE, ex.getMessage());
    }

    @ExceptionHandler(RateLimitedException.class)
    ResponseEntity<ApiError> rateLimited(RateLimitedException ex) {
        return error(429, ErrorCode.RATE_LIMITED, ex.getMessage());
    }

    @ExceptionHandler(AiUnavailableException.class)
    ResponseEntity<ApiError> aiUnavailable(AiUnavailableException ex) {
        return error(503, ErrorCode.AI_UNAVAILABLE, ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception ex) {
        log.error("Unhandled error (request {})", MDC.get("requestId"), ex);
        return error(500, ErrorCode.INTERNAL, "Something went wrong on our side. Try again.");
    }

    private static ResponseEntity<ApiError> fieldErrors(Map<String, String> fields) {
        return ResponseEntity.badRequest().body(new ApiError(400, ErrorCode.VALIDATION_FAILED, FIELDS, fields));
    }

    private static ResponseEntity<ApiError> error(int status, ErrorCode code, String message) {
        return ResponseEntity.status(status).body(ApiError.of(status, code, message));
    }
}
