package com.smart.exam.exam.model;

import java.util.List;

public class ExamPaper {

    private String sessionId;
    private String examId;
    private String paperId;
    private String paperName;
    private Integer totalScore;
    private Integer timeLimitMinutes;
    private List<ExamPaperQuestion> questions;

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getExamId() {
        return examId;
    }

    public void setExamId(String examId) {
        this.examId = examId;
    }

    public String getPaperId() {
        return paperId;
    }

    public void setPaperId(String paperId) {
        this.paperId = paperId;
    }

    public String getPaperName() {
        return paperName;
    }

    public void setPaperName(String paperName) {
        this.paperName = paperName;
    }

    public Integer getTotalScore() {
        return totalScore;
    }

    public void setTotalScore(Integer totalScore) {
        this.totalScore = totalScore;
    }

    public Integer getTimeLimitMinutes() {
        return timeLimitMinutes;
    }

    public void setTimeLimitMinutes(Integer timeLimitMinutes) {
        this.timeLimitMinutes = timeLimitMinutes;
    }

    public List<ExamPaperQuestion> getQuestions() {
        return questions;
    }

    public void setQuestions(List<ExamPaperQuestion> questions) {
        this.questions = questions;
    }
}

