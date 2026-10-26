package com.quiz.common;

public class Result<T> {
    public int code;
    public String message;
    public T data;
    public Result(int code, String message, T data) { this.code = code; this.message = message; this.data = data; }
    public static <T> Result<T> ok(T data) { return new Result<T>(0, "ok", data); }
    public static Result<Void> ok() { return ok(null); }
    public static Result<Void> error(int code, String message) { return new Result<Void>(code, message, null); }
}
