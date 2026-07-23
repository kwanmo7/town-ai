package com.townai.common.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

/**
 * Controller에서 발생한 예외를 공통 오류 응답으로 변환한다.
 *
 * <p>내부 예외 메시지와 Stack Trace는 응답에 노출하지 않는다.</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final Clock clock;

    /**
     * 공통 예외 처리기를 생성한다.
     *
     * @param clock 오류 응답 시각을 생성할 UTC Clock
     */
    public GlobalExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ErrorResponse> handleApiException(
            ApiException exception,
            HttpServletRequest request
    ) {
        return createResponse(exception.errorCode(), exception.getMessage(), request, List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        List<ErrorResponse.ValidationError> errors = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .sorted(Comparator.comparing(FieldError::getField))
                .map(error -> new ErrorResponse.ValidationError(
                        error.getField(),
                        error.getDefaultMessage() == null ? "올바르지 않은 값입니다." : error.getDefaultMessage()
                ))
                .toList();

        return createResponse(
                ErrorCode.VALIDATION_FAILED,
                ErrorCode.VALIDATION_FAILED.message(),
                request,
                errors
        );
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ErrorResponse> handleConstraintViolation(
            ConstraintViolationException exception,
            HttpServletRequest request
    ) {
        List<ErrorResponse.ValidationError> errors = exception.getConstraintViolations()
                .stream()
                .sorted(Comparator.comparing(violation -> violation.getPropertyPath().toString()))
                .map(this::toValidationError)
                .toList();

        return createResponse(
                ErrorCode.VALIDATION_FAILED,
                ErrorCode.VALIDATION_FAILED.message(),
                request,
                errors
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorResponse> handleHttpMessageNotReadable(
            HttpMessageNotReadableException exception,
            HttpServletRequest request
    ) {
        return createResponse(
                ErrorCode.MALFORMED_REQUEST,
                ErrorCode.MALFORMED_REQUEST.message(),
                request,
                List.of()
        );
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ErrorResponse> handleMethodNotAllowed(
            HttpRequestMethodNotSupportedException exception,
            HttpServletRequest request
    ) {
        return createResponse(
                ErrorCode.METHOD_NOT_ALLOWED,
                ErrorCode.METHOD_NOT_ALLOWED.message(),
                request,
                List.of()
        );
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ErrorResponse> handleMethodArgumentTypeMismatch(
            MethodArgumentTypeMismatchException exception,
            HttpServletRequest request
    ) {
        ErrorResponse.ValidationError error = new ErrorResponse.ValidationError(
                exception.getName(),
                "올바른 형식의 값을 입력해야 합니다."
        );
        return createResponse(
                ErrorCode.VALIDATION_FAILED,
                ErrorCode.VALIDATION_FAILED.message(),
                request,
                List.of(error)
        );
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> handleUnexpectedException(
            Exception exception,
            HttpServletRequest request
    ) {
        log.error("Unhandled exception. path={}", request.getRequestURI(), exception);

        return createResponse(
                ErrorCode.INTERNAL_SERVER_ERROR,
                ErrorCode.INTERNAL_SERVER_ERROR.message(),
                request,
                List.of()
        );
    }

    private ErrorResponse.ValidationError toValidationError(ConstraintViolation<?> violation) {
        return new ErrorResponse.ValidationError(
                violation.getPropertyPath().toString(),
                violation.getMessage()
        );
    }

    private ResponseEntity<ErrorResponse> createResponse(
            ErrorCode errorCode,
            String message,
            HttpServletRequest request,
            List<ErrorResponse.ValidationError> errors
    ) {
        ErrorResponse response = new ErrorResponse(
                Instant.now(clock).truncatedTo(ChronoUnit.SECONDS),
                errorCode.status().value(),
                errorCode.name(),
                message,
                request.getRequestURI(),
                errors
        );

        return ResponseEntity.status(errorCode.status()).body(response);
    }
}
