package com.example.backend.exception;

import com.example.backend.common.Result;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器，自动拦截 Controller 层抛出的异常。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 处理参数校验异常（@Valid 注解触发） */
    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            BindException.class,
            ConstraintViolationException.class
    })
    public Result<Void> handleValidation(Exception ex) {
        log.warn("参数校验失败: {}", ex.getMessage());
        return Result.fail(400, "参数校验失败");
    }

    /** 处理 JSON 格式错误的请求体 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleBadJson(HttpMessageNotReadableException ex) {
        log.warn("请求体格式错误: {}", ex.getMessage());
        return Result.fail(400, "请求体格式错误");
    }

    /** 兜底异常处理，捕获所有未明确处理的异常 */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception ex) {
        log.error("未处理异常 — {}: {}", ex.getClass().getSimpleName(), ex.getMessage(), ex);
        return Result.fail(ex.getMessage());
    }
}
