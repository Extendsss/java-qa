package com.quiz.common;

import java.util.List;

public class Page<T> {
    public long total;
    public List<T> list;
    public static <T> Page<T> of(long total, List<T> list) { Page<T> result = new Page<T>(); result.total = total; result.list = list; return result; }
}
