package com.quiz.entity.dto;

import javax.validation.constraints.*;

public class PracticeRequest {
    @NotBlank @Pattern(regexp="RANDOM|ORDER|WRONG") public String mode;
    public Long resumeId, categoryId;
    @Pattern(regexp="QA|SINGLE|MULTI|JUDGE|FILL") public String type;
    @Min(1) @Max(3) public Integer difficulty;
    @Min(1) @Max(100) public Integer count=10;
    @Min(0) @Max(1000000) public Integer startIndex=0;
}
