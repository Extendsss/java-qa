package com.quiz.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quiz.common.BizException;
import com.quiz.entity.*;
import com.quiz.entity.dto.QuestionRequest;
import com.quiz.mapper.CategoryMapper;
import com.quiz.mapper.QuestionMapper;
import com.quiz.security.SessionService;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class QuestionResumeOwnershipTest {
    private QuestionMapper questions;
    private ResumeService resumes;
    private SessionService session;
    private QuestionService service;
    private Resume resume;
    private Map<Long, Question> stored;

    @BeforeEach void setup() {
        questions = mock(QuestionMapper.class);
        CategoryMapper categories = mock(CategoryMapper.class);
        resumes = mock(ResumeService.class);
        session = mock(SessionService.class);
        service = new QuestionService(questions, categories, resumes, session, new ObjectMapper());
        SysUser user = new SysUser(); user.id=99L; user.role="ADMIN";
        when(session.current()).thenReturn(user);
        when(session.isAdmin()).thenReturn(true);
        when(session.visible(anyLong(), anyInt())).thenReturn(true);
        when(categories.findById(1L)).thenReturn(new Category());
        resume = new Resume(); resume.id=10L; resume.ownerId=7L; resume.isPublic=0;
        when(resumes.requireEditable(10L)).thenReturn(resume);
        when(resumes.requireVisible(10L)).thenReturn(resume);
        stored = new HashMap<>();
        when(questions.findById(anyLong())).thenAnswer(invocation -> stored.get((Long)invocation.getArgument(0)));
        when(questions.lockById(anyLong())).thenAnswer(invocation -> stored.get((Long)invocation.getArgument(0)));
        when(questions.insert(any())).thenAnswer(invocation -> {
            Question q = invocation.getArgument(0); q.id=88L; stored.put(q.id,q); return 1;
        });
    }

    private QuestionRequest request() {
        QuestionRequest request = new QuestionRequest();
        request.categoryId=1L; request.type="QA"; request.difficulty=2;
        request.title="项目中为什么使用缓存？"; request.content="请说明缓存策略"; request.answer="结合项目说明读写策略";
        return request;
    }

    private Question parent() {
        Question parent = new Question();
        parent.id=20L; parent.ownerId=7L; parent.isPublic=0; parent.resumeId=10L; parent.blockId=30L;
        stored.put(parent.id,parent);
        return parent;
    }

    @Test void adminCreatingForPrivateResumeInheritsResumeOwnerAndVisibility() {
        QuestionRequest request = request(); request.resumeId=10L;
        Question saved = service.create(request);
        assertEquals(Long.valueOf(7L), saved.ownerId);
        assertEquals(Integer.valueOf(0), saved.isPublic);
        assertEquals(Long.valueOf(10L), saved.resumeId);
        assertNull(saved.rootId);
        assertNull(saved.parentId);
    }

    @Test void followUpInfersResumeAndBlockFromParent() {
        parent();
        QuestionRequest request = request(); request.parentId=20L;
        Question saved = service.create(request);
        assertEquals(Long.valueOf(10L), saved.resumeId);
        assertEquals(Long.valueOf(30L), saved.blockId);
        assertEquals(Long.valueOf(20L), saved.rootId);
        assertEquals(Long.valueOf(20L), saved.parentId);
        assertEquals(Long.valueOf(7L), saved.ownerId);
        assertEquals(Integer.valueOf(0), saved.isPublic);
        verify(resumes).requireBlock(30L,10L);
    }

    @Test void followUpCannotConnectTwoResumes() {
        parent();
        QuestionRequest request = request(); request.parentId=20L; request.resumeId=11L;
        assertThrows(BizException.class, () -> service.create(request));
        verify(questions,never()).insert(any());
    }

    @Test void foreignResumeBlockIsRejectedBeforeInsert() {
        QuestionRequest request = request(); request.resumeId=10L; request.blockId=31L;
        when(resumes.requireBlock(31L,10L)).thenThrow(new BizException(400,"关联分块不属于该简历"));
        assertThrows(BizException.class, () -> service.create(request));
        verify(questions,never()).insert(any());
    }

    @Test void ordinaryQuestionCannotLinkResumeBlock() {
        QuestionRequest request = request(); request.blockId=30L;
        assertThrows(BizException.class, () -> service.create(request));
        verify(questions,never()).insert(any());
    }

    @Test void publicResumeCannotBeExtendedByItsOriginalOwner() {
        QuestionRequest request = request(); request.resumeId=10L;
        when(session.isAdmin()).thenReturn(false);
        when(resumes.requireEditable(10L)).thenThrow(new BizException(403,"公共内容仅管理员可编辑"));
        assertThrows(BizException.class, () -> service.create(request));
        verify(questions,never()).insert(any());
    }

    @Test void editCannotMoveQuestionIntoAnotherResume() {
        parent();
        QuestionRequest request = request(); request.resumeId=11L;
        assertThrows(BizException.class, () -> service.update(20L, request));
        verify(questions,never()).update(any());
    }

    @Test void deeperFollowUpKeepsOriginalChainRoot() {
        Question parent = parent(); parent.rootId=5L;
        Question root = new Question(); root.id=5L; root.ownerId=7L; root.isPublic=0; root.resumeId=10L;
        stored.put(root.id,root);
        QuestionRequest request = request(); request.parentId=20L;
        Question saved = service.create(request);
        assertEquals(Long.valueOf(5L), saved.rootId);
        assertEquals(Long.valueOf(20L), saved.parentId);
    }
}
