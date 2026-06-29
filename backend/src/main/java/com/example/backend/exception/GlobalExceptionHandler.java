package com.example.backend.exception;

import com.example.backend.common.Result;
import javax.validation.ConstraintViolationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// 全局异常处理器，自动拦截Controller层抛出的异常
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 处理参数校验异常（@Valid注解触发）
    @ExceptionHandler({
            MethodArgumentNotValidException.class,  // @RequestBody参数校验失败
            BindException.class,                     // @ModelAttribute参数绑定失败
            ConstraintViolationException.class       // 单个参数校验失败
    })
    public Result<Void> handleValidation(Exception ex) {
        return Result.fail(400, "参数校验失败");
    }

    // 处理JSON格式错误的请求体
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleBadJson(HttpMessageNotReadableException ex) {
        return Result.fail(400, "请求体格式错误");
    }

    // 兜底异常处理，捕获所有未明确处理的异常
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception ex) {
        return Result.fail(ex.getMessage());
    }
}
