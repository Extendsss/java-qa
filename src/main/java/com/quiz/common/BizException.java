package com.quiz.common;

public class BizException extends RuntimeException {
    public final int code;
    public BizException(int code, String message) { super(message); this.code = code; }
}
