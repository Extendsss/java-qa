package com.quiz.entity.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.quiz.common.PageQuery;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.*;

public final class ResumeRequests {
    private ResumeRequests() {}

    public static class Create {
        @NotBlank @Size(max = 200) public String title;
        @Valid @Size(max = 200) public List<BlockCreate> blocks;
    }

    public static class Update {
        @NotBlank @Size(max = 200) public String title;
    }

    public static class BlockCreate {
        @NotNull @Positive public Long blockDefId;
        public Integer sort = 0;
        @NotNull public JsonNode content;
    }

    public static class BlockUpdate {
        public Integer sort;
        @NotNull public JsonNode content;
    }

    public static class Visibility {
        @NotNull @Min(0) @Max(1) public Integer isPublic;
    }

    public static class Query extends PageQuery {
        public String keyword;
        public Long ownerId;
        public Integer isPublic;
    }

    public static class Definition {
        @Size(max = 50) public String code;
        @NotBlank @Size(max = 50) public String name;
        @NotNull public JsonNode fieldSchema;
        @NotNull @Min(0) @Max(1) public Integer repeatable = 0;
        @NotNull @Min(0) @Max(1) public Integer enabled = 1;
        public Integer sort = 0;
    }
}
