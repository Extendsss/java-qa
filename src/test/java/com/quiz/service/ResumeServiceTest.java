package com.quiz.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quiz.common.BizException;
import com.quiz.entity.*;
import com.quiz.entity.dto.ResumeRequests;
import com.quiz.mapper.BlockDefMapper;
import com.quiz.mapper.ResumeMapper;
import com.quiz.security.SessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ResumeServiceTest {
    private ResumeMapper mapper;
    private BlockDefMapper definitions;
    private BlockDefService blockDefs;
    private SessionService session;
    private ResumeService service;
    private Resume resume;

    @BeforeEach void setup() {
        mapper = mock(ResumeMapper.class);
        definitions = mock(BlockDefMapper.class);
        blockDefs = mock(BlockDefService.class);
        session = mock(SessionService.class);
        service = new ResumeService(mapper, definitions, blockDefs, session);
        resume = new Resume(); resume.id = 1L; resume.ownerId = 7L; resume.isPublic = 0; resume.status = 1;
        when(mapper.findById(1L)).thenReturn(resume);
        when(mapper.lockById(1L)).thenReturn(resume);
    }

    @Test void privateResumesAreHiddenFromOtherUsers() {
        when(session.visible(7L, 0)).thenReturn(false);
        assertThrows(BizException.class, () -> service.requireVisible(1L));
    }

    @Test void publicationChangesResumeAndEveryQuestionTogether() {
        when(session.isAdmin()).thenReturn(true);
        service.setPublic(1L, 1);
        verify(mapper).lockById(1L);
        verify(mapper).setPublic(1L, 1);
        verify(mapper).setQuestionsPublic(1L, 1);
    }

    @Test void publicContentsDenyOwnerMutation() {
        resume.isPublic = 1;
        doThrow(new BizException(403, "只读")).when(session).requireEdit(7L, 1);
        assertThrows(BizException.class, () -> service.delete(1L));
        verify(mapper, never()).deleteQuestions(anyLong());
    }

    @Test void deletingResumeCascadesAllContentsAndQuestions() {
        service.delete(1L);
        verify(mapper).deleteQuestions(1L);
        verify(mapper).deleteBlocks(1L);
        verify(mapper).softDelete(1L);
    }

    @Test void singleBlocksCannotBeAddedTwice() throws Exception {
        BlockDef definition = new BlockDef(); definition.id=2L; definition.enabled=1; definition.repeatable=0;
        when(blockDefs.require(2L)).thenReturn(definition);
        when(mapper.countBlock(1L, 2L)).thenReturn(1L);
        ResumeRequests.BlockCreate request = new ResumeRequests.BlockCreate();
        request.blockDefId=2L; request.content=new ObjectMapper().readTree("{}");
        assertThrows(BizException.class, () -> service.addBlock(1L, request));
        verify(mapper, never()).insertBlock(any());
    }

    @Test void relationMustPointToLiveBlockInSameResume() {
        ResumeBlock block = new ResumeBlock(); block.id=3L; block.resumeId=2L;
        when(mapper.lockBlock(3L)).thenReturn(block);
        assertThrows(BizException.class, () -> service.requireBlock(3L, 1L));
        assertThrows(BizException.class, () -> service.requireBlock(4L, 1L));
    }

    @Test void disabledDefinitionBlocksNewEntries() {
        BlockDef definition = new BlockDef(); definition.id=2L; definition.enabled=0; definition.repeatable=1;
        when(blockDefs.require(2L)).thenReturn(definition);
        ResumeRequests.BlockCreate request = new ResumeRequests.BlockCreate(); request.blockDefId=2L;
        assertThrows(BizException.class, () -> service.addBlock(1L, request));
        verify(mapper, never()).insertBlock(any());
    }

    @Test void newResumeOwnerAndVisibilityAreServerAssigned() {
        SysUser user = new SysUser(); user.id=7L;
        when(session.current()).thenReturn(user);
        when(session.isAdmin()).thenReturn(false);
        doAnswer(invocation -> { ((Resume)invocation.getArgument(0)).id=1L; return 1; }).when(mapper).insert(any());
        ResumeRequests.Create request = new ResumeRequests.Create(); request.title="私有简历";
        service.create(request);
        ArgumentCaptor<Resume> saved = ArgumentCaptor.forClass(Resume.class);
        verify(mapper).insert(saved.capture());
        assertEquals(Long.valueOf(7L), saved.getValue().ownerId);
        assertEquals(Integer.valueOf(0), saved.getValue().isPublic);
    }
}
