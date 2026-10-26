package com.quiz.entity.dto;

import com.quiz.common.BizException;
import com.quiz.common.PageQuery;
import java.util.Map;

public class QuestionQuery extends PageQuery {
    public Long categoryId, ownerId, rootId, resumeId, userId;
    public String type, keyword;
    public Integer difficulty, isPublic;
    public boolean admin, mine, ordinary = true, wrong, random;
    public int offset, limit;

    public static QuestionQuery from(Map<String,String> values) {
        QuestionQuery q = new QuestionQuery();
        try {
            q.page = integer(values.get("page"), 1);
            q.size = integer(values.get("size"), 10);
            q.categoryId = number(values.get("categoryId"));
            q.ownerId = number(values.get("ownerId"));
            q.rootId = number(values.get("rootId"));
            q.resumeId = number(values.get("resumeId"));
            q.difficulty = integer(values.get("difficulty"), null);
            q.isPublic = integer(values.get("isPublic"), null);
            q.type = empty(values.get("type"));
            q.keyword = empty(values.get("keyword"));
            q.mine = "1".equals(values.get("mine"));
        } catch (NumberFormatException e) { throw new BizException(400, "筛选参数格式错误"); }
        q.offset = q.getOffset(); q.limit = q.getSize();
        return q;
    }
    private static String empty(String s) { return s == null || s.trim().isEmpty() ? null : s.trim(); }
    private static Long number(String s) { return empty(s) == null ? null : Long.valueOf(s); }
    private static Integer integer(String s, Integer fallback) { return empty(s) == null ? fallback : Integer.valueOf(s); }
}
