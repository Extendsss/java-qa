package com.quiz.service;

import com.quiz.common.BizException;
import com.quiz.common.Checks;
import com.quiz.common.Page;
import com.quiz.entity.BlockDef;
import com.quiz.entity.Resume;
import com.quiz.entity.ResumeBlock;
import com.quiz.entity.dto.ResumeRequests;
import com.quiz.mapper.BlockDefMapper;
import com.quiz.mapper.ResumeMapper;
import com.quiz.security.SessionService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ResumeService {
    private final ResumeMapper mapper;
    private final BlockDefMapper definitions;
    private final BlockDefService blockDefs;
    private final SessionService session;

    public ResumeService(ResumeMapper mapper, BlockDefMapper definitions, BlockDefService blockDefs, SessionService session) {
        this.mapper = mapper;
        this.definitions = definitions;
        this.blockDefs = blockDefs;
        this.session = session;
    }

    public Resume requireVisible(Long id) {
        Resume resume = mapper.findById(id);
        if (resume == null || !session.visible(resume.ownerId, resume.isPublic)) throw new BizException(404, "简历不存在");
        return resume;
    }

    /** Call inside the caller's transaction to serialize mutations with publication/deletion. */
    public Resume requireEditable(Long id) {
        Resume resume = mapper.lockById(id);
        if (resume == null) throw new BizException(404, "简历不存在");
        session.requireEdit(resume.ownerId, resume.isPublic);
        return resume;
    }

    public ResumeBlock requireBlock(Long blockId, Long resumeId) {
        ResumeBlock block = mapper.lockBlock(blockId);
        Checks.require(block != null && resumeId != null && resumeId.equals(block.resumeId), "关联分块不属于该简历或已删除");
        return block;
    }

    public Page<Resume> list(ResumeRequests.Query query) {
        Checks.require(query.keyword == null || query.keyword.length() <= 200, "搜索词最多 200 字");
        Checks.require(query.isPublic == null || query.isPublic == 0 || query.isPublic == 1, "公私筛选值不正确");
        Long userId = session.current().id;
        boolean admin = session.isAdmin();
        return Page.of(mapper.count(query, userId, admin), mapper.list(query, userId, admin));
    }

    @Transactional
    public Resume create(ResumeRequests.Create request) {
        definitions.lockDefinitions();
        Resume resume = new Resume();
        resume.title = request.title.trim();
        resume.ownerId = session.current().id;
        resume.isPublic = session.isAdmin() ? 1 : 0;
        resume.status = 1;
        mapper.insert(resume);
        if (request.blocks != null) for (ResumeRequests.BlockCreate block : request.blocks) insertBlock(resume, block);
        return mapper.findById(resume.id);
    }

    @Transactional
    public Resume update(Long id, ResumeRequests.Update request) {
        Resume resume = requireEditable(id);
        resume.title = request.title.trim();
        mapper.updateTitle(resume);
        return mapper.findById(id);
    }

    @Transactional
    public void delete(Long id) {
        requireEditable(id);
        mapper.deleteQuestions(id);
        mapper.deleteBlocks(id);
        mapper.softDelete(id);
    }

    @Transactional
    public Resume setPublic(Long id, Integer isPublic) {
        Checks.require(session.isAdmin(), "仅管理员可调整公开状态");
        Checks.require(isPublic != null && (isPublic == 0 || isPublic == 1), "公开状态必须为 0 或 1");
        requireEditable(id);
        mapper.setPublic(id, isPublic);
        mapper.setQuestionsPublic(id, isPublic);
        return mapper.findById(id);
    }

    public List<ResumeBlock> blocks(Long id) {
        requireVisible(id);
        return mapper.blocks(id);
    }

    @Transactional
    public ResumeBlock addBlock(Long id, ResumeRequests.BlockCreate request) {
        definitions.lockDefinitions();
        return insertBlock(requireEditable(id), request);
    }

    private ResumeBlock insertBlock(Resume resume, ResumeRequests.BlockCreate request) {
        BlockDef definition = blockDefs.require(request.blockDefId);
        Checks.require(Integer.valueOf(1).equals(definition.enabled), "分块已停用，不能新增条目");
        if (Integer.valueOf(0).equals(definition.repeatable) && mapper.countBlock(resume.id, definition.id) > 0)
            throw new BizException(1008, "该分块为单条分块，每份简历只能录入一条");
        ResumeBlock block = new ResumeBlock();
        block.resumeId = resume.id;
        block.blockDefId = definition.id;
        block.sort = request.sort == null ? 0 : request.sort;
        block.content = blockDefs.validateContent(definition, request.content);
        block.status = 1;
        mapper.insertBlock(block);
        return mapper.findBlock(block.id);
    }

    @Transactional
    public ResumeBlock updateBlock(Long blockId, ResumeRequests.BlockUpdate request) {
        definitions.lockDefinitions();
        ResumeBlock block = findBlock(blockId);
        requireEditable(block.resumeId);
        block = requireBlock(blockId, block.resumeId);
        BlockDef definition = blockDefs.require(block.blockDefId);
        block.content = blockDefs.validateContent(definition, request.content);
        if (request.sort != null) block.sort = request.sort;
        mapper.updateBlock(block);
        return mapper.findBlock(blockId);
    }

    @Transactional
    public void deleteBlock(Long blockId) {
        ResumeBlock block = findBlock(blockId);
        requireEditable(block.resumeId);
        requireBlock(blockId, block.resumeId);
        mapper.detachBlock(blockId);
        mapper.deleteBlock(blockId);
    }

    private ResumeBlock findBlock(Long blockId) {
        ResumeBlock block = mapper.findBlock(blockId);
        if (block == null) throw new BizException(404, "简历条目不存在");
        return block;
    }
}
