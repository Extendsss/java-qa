package com.quiz.service;

import com.quiz.common.*;
import com.quiz.entity.*;
import com.quiz.entity.dto.*;
import com.quiz.mapper.*;
import com.quiz.security.SessionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class PracticeService {
    private final QuestionMapper questions;
    private final PracticeMapper mapper;
    private final QuestionService questionService;
    private final ResumeService resumes;
    private final SessionService session;
    public PracticeService(QuestionMapper questions,PracticeMapper mapper,QuestionService questionService,ResumeService resumes,SessionService session) {
        this.questions=questions;this.mapper=mapper;this.questionService=questionService;this.resumes=resumes;this.session=session;
    }
    private QuestionQuery scope(QuestionQuery query) { query.userId=session.current().id; query.admin=session.isAdmin(); return query; }
    public List<Question> start(PracticeRequest request) {
        QuestionQuery query=scope(new QuestionQuery());
        query.limit=request.count==null?10:request.count;
        query.offset="ORDER".equals(request.mode) && request.startIndex!=null?request.startIndex:0;
        query.random="RANDOM".equals(request.mode); query.wrong="WRONG".equals(request.mode);
        query.ordinary=!query.wrong;
        if(request.resumeId!=null) { resumes.requireVisible(request.resumeId); query.resumeId=request.resumeId; query.ordinary=false; }
        else { query.categoryId=request.categoryId;query.type=request.type;query.difficulty=request.difficulty; }
        List<Question> rows=questions.list(query); rows.forEach(questionService::hideAnswer); return rows;
    }
    public Map<String,Object> reveal(SubmitRequest request) {
        Question q=questionService.requireVisible(request.questionId);
        Checks.require("QA".equals(q.type),"客观题请提交作答后查看答案");
        return feedback(q,null);
    }
    @Transactional
    public Map<String,Object> submit(SubmitRequest request) {
        Question q=questionService.requireVisible(request.questionId);
        boolean correct;
        if("QA".equals(q.type)) { Checks.require(request.selfCorrect!=null,"问答题提交需要自评对错（selfCorrect）"); correct=request.selfCorrect==1; }
        else correct=GradingService.judge(q.type,q.answer,request.userAnswer);
        AnswerRecord record=new AnswerRecord(); record.userId=session.current().id;record.questionId=q.id;
        record.userAnswer=request.userAnswer; record.isCorrect=correct?1:0;
        mapper.insert(record);
        return feedback(q,correct);
    }
    private Map<String,Object> feedback(Question q,Boolean correct) {
        Map<String,Object> result=new LinkedHashMap<>(); result.put("rightAnswer",q.answer);result.put("analysis",q.analysis);
        result.put("selfJudge","QA".equals(q.type));result.put("hasFollowUp",Boolean.TRUE.equals(q.hasFollowUp));
        if(correct!=null) result.put("correct",correct);
        return result;
    }
    public Page<AnswerRecord> records(QuestionQuery query) { scope(query); return Page.of(mapper.recordCount(query),mapper.records(query)); }
    public Page<Question> wrong(QuestionQuery query) {
        scope(query);query.wrong=true; query.ordinary=false;
        List<Question> rows=questions.list(query);rows.forEach(questionService::hideAnswer);
        return Page.of(questions.count(query),rows);
    }
    public Map<String,Object> stats() {
        QuestionQuery query=scope(new QuestionQuery());
        Map<String,Object> totals=mapper.totals(query);
        long total=((Number)totals.get("totalAnswered")).longValue();
        long correct=((Number)totals.get("correctCount")).longValue();
        totals.put("accuracy",accuracy(total,correct));
        List<Map<String,Object>> groups=mapper.byCategory(query);
        for(Map<String,Object> group:groups) group.put("accuracy",accuracy(((Number)group.get("total")).longValue(),((Number)group.get("correct")).longValue()));
        totals.put("byCategory",groups);return totals;
    }
    private double accuracy(long total,long correct) { return total==0?0:Math.round(correct*10000.0/total)/100.0; }
}
