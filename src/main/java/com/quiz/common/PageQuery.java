package com.quiz.common;

public class PageQuery {
    public int page = 1;
    public int size = 10;
    public int getOffset() { return (Math.min(Math.max(page, 1), 1000000) - 1) * getSize(); }
    public int getSize() { return Math.min(Math.max(size, 1), 100); }
    public void setPage(int page) { this.page = page; }
    public void setSize(int size) { this.size = size; }
}
