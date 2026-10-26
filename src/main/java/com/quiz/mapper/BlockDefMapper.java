package com.quiz.mapper;

import com.quiz.entity.BlockDef;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface BlockDefMapper {
    List<BlockDef> list(@Param("enabledOnly") boolean enabledOnly);
    List<Long> lockDefinitions();
    BlockDef findById(Long id);
    BlockDef findByCode(String code);
    long countActive();
    long countReferences(Long id);
    long countDuplicateSingle(Long id);
    int insert(BlockDef definition);
    int update(BlockDef definition);
    int softDelete(Long id);
}
