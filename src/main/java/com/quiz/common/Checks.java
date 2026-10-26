package com.quiz.common;

public final class Checks {
    private Checks() { }
    public static void require(boolean condition, String message) { if (!condition) throw new BizException(400, message); }
}
