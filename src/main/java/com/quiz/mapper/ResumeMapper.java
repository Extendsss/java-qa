package com.quiz.mapper;

import com.quiz.entity.Resume;
import com.quiz.entity.ResumeBlock;
import com.quiz.entity.dto.ResumeRequests;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface ResumeMapper {
    Resume findById(Long id);
    Resume lockById(Long id);
    List<Resume> list(@Param("query") ResumeRequests.Query query, @Param("userId") Long userId, @Param("admin") boolean admin);
    long count(@Param("query") ResumeRequests.Query query, @Param("userId") Long userId, @Param("admin") boolean admin);
    int insert(Resume resume);
    int updateTitle(Resume resume);
    int softDelete(Long id);
    int setPublic(@Param("id") Long id, @Param("isPublic") Integer isPublic);
    int setQuestionsPublic(@Param("id") Long id, @Param("isPublic") Integer isPublic);
    int deleteQuestions(Long id);
    int deleteBlocks(Long id);
    List<ResumeBlock> blocks(Long resumeId);
    ResumeBlock findBlock(Long id);
    ResumeBlock lockBlock(Long id);
    long countBlock(@Param("resumeId") Long resumeId, @Param("blockDefId") Long blockDefId);
    int insertBlock(ResumeBlock block);
    int updateBlock(ResumeBlock block);
    int deleteBlock(Long id);
    int detachBlock(Long id);
}
