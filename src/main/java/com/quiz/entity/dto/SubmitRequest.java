package com.quiz.entity.dto;

import javax.validation.constraints.*;

public class SubmitRequest {
    @NotNull public Long questionId;
    @Size(max=50000) public String userAnswer;
    @Min(0) @Max(1) public Integer selfCorrect;
}
