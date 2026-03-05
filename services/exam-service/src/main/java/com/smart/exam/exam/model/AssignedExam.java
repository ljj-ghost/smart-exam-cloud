package com.smart.exam.exam.model;

import java.time.LocalDateTime;

public class AssignedExam {

    private String examId;
    private String paperId;
    private String title;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer antiCheatLevel;
    private ExamStatus status;
    private String sessionId;
    private SessionStatus sessionStatus;
    private LocalDateTime sessionStartTime;
    private LocalDateTime sessionSubmitTime;

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

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public Integer getAntiCheatLevel() {
        return antiCheatLevel;
    }

    public void setAntiCheatLevel(Integer antiCheatLevel) {
        this.antiCheatLevel = antiCheatLevel;
    }

    public ExamStatus getStatus() {
        return status;
    }

    public void setStatus(ExamStatus status) {
        this.status = status;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public SessionStatus getSessionStatus() {
        return sessionStatus;
    }

    public void setSessionStatus(SessionStatus sessionStatus) {
        this.sessionStatus = sessionStatus;
    }

    public LocalDateTime getSessionStartTime() {
        return sessionStartTime;
    }

    public void setSessionStartTime(LocalDateTime sessionStartTime) {
        this.sessionStartTime = sessionStartTime;
    }

    public LocalDateTime getSessionSubmitTime() {
        return sessionSubmitTime;
    }

    public void setSessionSubmitTime(LocalDateTime sessionSubmitTime) {
        this.sessionSubmitTime = sessionSubmitTime;
    }
}

