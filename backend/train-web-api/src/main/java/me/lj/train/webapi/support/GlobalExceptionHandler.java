package me.lj.train.webapi.support;

import jakarta.validation.ConstraintViolationException;
import me.lj.train.common.core.exception.BusinessException;
import me.lj.train.common.core.result.AppErrorCode;
import me.lj.train.common.core.result.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * REST接口统一异常处理。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<?>> handleBusinessException(BusinessException exception) {
        return ResponseEntity
                .status(exception.getErrorCode().getHttpStatus())
                .body(Result.failed(exception.getErrorCode(), exception.getMessage()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseEntity<Result<?>> handleValidationException(Exception exception) {
        String message;
        if (exception instanceof MethodArgumentNotValidException validationException) {
            message = validationException.getBindingResult().getFieldErrors().isEmpty()
                    ? AppErrorCode.PARAM_INVALID.getMessage()
                    : validationException.getBindingResult().getFieldErrors().get(0).getDefaultMessage();
        } else {
            BindException bindException = (BindException) exception;
            message = bindException.getFieldErrors().isEmpty()
                    ? AppErrorCode.PARAM_INVALID.getMessage()
                    : bindException.getFieldErrors().get(0).getDefaultMessage();
        }
        return ResponseEntity.badRequest().body(Result.failed(AppErrorCode.PARAM_INVALID, message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<?>> handleUnreadable() {
        return ResponseEntity.badRequest().body(Result.failed(AppErrorCode.PARAM_INVALID));
    }

    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            ConstraintViolationException.class
    })
    public ResponseEntity<Result<?>> handleRequestParameter(Exception exception) {
        return ResponseEntity.badRequest().body(
                Result.failed(AppErrorCode.PARAM_INVALID, exception.getMessage()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Result<?>> handleNotFound() {
        return ResponseEntity
                .status(AppErrorCode.RESOURCE_NOT_FOUND.getHttpStatus())
                .body(Result.failed(AppErrorCode.RESOURCE_NOT_FOUND));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Result<?>> handleMethodNotAllowed() {
        return ResponseEntity
                .status(AppErrorCode.METHOD_NOT_ALLOWED.getHttpStatus())
                .body(Result.failed(AppErrorCode.METHOD_NOT_ALLOWED));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<?>> handleUnexpected(Exception exception) {
        LOGGER.error("未处理的系统异常", exception);
        return ResponseEntity
                .status(AppErrorCode.SYSTEM_ERROR.getHttpStatus())
                .body(Result.failed(AppErrorCode.SYSTEM_ERROR));
    }
}
