package com.quiz.common;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotRoleException;
import javax.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.validation.BindException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    @ExceptionHandler(BizException.class) public Result<Void> business(BizException ex) { return Result.error(ex.code, ex.getMessage()); }
    @ExceptionHandler(NotLoginException.class) public Result<Void> login(NotLoginException ex) { return Result.error(401, "请先登录"); }
    @ExceptionHandler(NotRoleException.class) public Result<Void> role(NotRoleException ex) { return Result.error(403, "无权限访问"); }
    @ExceptionHandler(MethodArgumentNotValidException.class) public Result<Void> validation(MethodArgumentNotValidException ex) { return Result.error(400, ex.getBindingResult().getFieldErrors().get(0).getDefaultMessage()); }
    @ExceptionHandler(BindException.class) public Result<Void> binding(BindException ex) { return Result.error(400, "请求参数格式错误"); }
    @ExceptionHandler({ConstraintViolationException.class, HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class, IllegalArgumentException.class}) public Result<Void> invalid(Exception ex) { return Result.error(400, "请求参数格式错误"); }
    @ExceptionHandler(DuplicateKeyException.class) public Result<Void> duplicate(DuplicateKeyException ex) { return Result.error(400, "名称或编码已存在"); }
    @ExceptionHandler(NoHandlerFoundException.class) public Result<Void> notFound(NoHandlerFoundException ex) { return Result.error(404, "接口不存在"); }
    @ExceptionHandler(Exception.class) public Result<Void> unexpected(Exception ex) { log.error("Unhandled request error", ex); return Result.error(500, "服务器内部错误，请稍后重试"); }
}
