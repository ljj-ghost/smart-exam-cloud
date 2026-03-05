package com.smart.exam.exam.model;

import jakarta.validation.constraints.NotBlank;

public class AnswerItem {

    @NotBlank
    private String questionId;
    private Object answerContent;
    private Boolean markedForReview;

    public String getQuestionId() {
        return questionId;
    }

    public void setQuestionId(String questionId) {
        this.questionId = questionId;
    }

    public Object getAnswerContent() {
        return answerContent;
    }

    public void setAnswerContent(Object answerContent) {
        this.answerContent = answerContent;
    }

    public Boolean getMarkedForReview() {
        return markedForReview;
    }

    public void setMarkedForReview(Boolean markedForReview) {
        this.markedForReview = markedForReview;
    }
}
