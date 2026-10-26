package com.quiz.entity.dto;

import com.fasterxml.jackson.databind.JsonNode;
import javax.validation.constraints.*;

public class QuestionRequest {
    @NotNull public Long categoryId;
    @NotBlank @Pattern(regexp="QA|SINGLE|MULTI|JUDGE|FILL") public String type;
    @NotNull @Min(1) @Max(3) public Integer difficulty;
    @NotBlank @Size(max=200) public String title;
    @NotBlank @Size(max=50000) public String content;
    public JsonNode options;
    @NotBlank @Size(max=50000) public String answer;
    @Size(max=50000) public String analysis;
    public Long parentId, resumeId, blockId;
}
