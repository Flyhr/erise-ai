package com.erise.ai.backend.common.exception;

import com.erise.ai.backend.common.api.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public ResponseEntity<ApiResponse<Void>> handleBiz(BizException exception) {
        return ResponseEntity.status(exception.getStatus())
                .body(ApiResponse.failure(exception.getCode(), exception.getMessage()));
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            BindException.class,
            ConstraintViolationException.class,
            IllegalArgumentException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(Exception exception) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.failure(ErrorCodes.BAD_REQUEST, resolveValidationMessage(exception)));
    }


    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleUploadTooLarge(MaxUploadSizeExceededException exception) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.failure(ErrorCodes.BAD_REQUEST, "Uploaded file exceeds the 50MB limit. Please compress it and retry."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleOther(Exception exception) {
        return ResponseEntity.internalServerError()
                .body(ApiResponse.failure(ErrorCodes.SERVER_ERROR, exception.getMessage()));
    }

    private String resolveValidationMessage(Exception exception) {
        if (exception instanceof MethodArgumentNotValidException methodArgumentNotValidException) {
            FieldError fieldError = methodArgumentNotValidException.getBindingResult().getFieldError();
            if (fieldError != null && fieldError.getDefaultMessage() != null && !fieldError.getDefaultMessage().isBlank()) {
                return fieldError.getDefaultMessage();
            }
        }
        if (exception instanceof BindException bindException) {
            FieldError fieldError = bindException.getBindingResult().getFieldError();
            if (fieldError != null && fieldError.getDefaultMessage() != null && !fieldError.getDefaultMessage().isBlank()) {
                return fieldError.getDefaultMessage();
            }
        }
        if (exception instanceof ConstraintViolationException constraintViolationException
                && !constraintViolationException.getConstraintViolations().isEmpty()) {
            return constraintViolationException.getConstraintViolations().stream()
                    .map(violation -> violation.getMessage())
                    .filter(message -> message != null && !message.isBlank())
                    .collect(Collectors.joining("；"));
        }
        return exception.getMessage();
    }
}
