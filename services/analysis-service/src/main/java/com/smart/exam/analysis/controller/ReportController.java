package com.smart.exam.analysis.controller;

import com.smart.exam.analysis.service.ReportDomainService;
import com.smart.exam.common.core.model.ApiResponse;
import com.smart.exam.common.web.security.PermissionCodes;
import com.smart.exam.common.web.security.RoleGuard;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    private final ReportDomainService reportDomainService;

    public ReportController(ReportDomainService reportDomainService) {
        this.reportDomainService = reportDomainService;
    }

    @GetMapping("/exams/{examId}/score-distribution")
    public ApiResponse<Map<String, Object>> scoreDistribution(
            @PathVariable("examId") String examId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Role", required = false) String role,
            @RequestHeader(value = "X-Permissions", required = false) String permissions) {
        String operatorId = requireTeacherOrAdmin(userId, role, permissions, PermissionCodes.REPORT_SCORE_DISTRIBUTION_VIEW);
        return ApiResponse.ok(reportDomainService.scoreDistribution(examId, operatorId, role));
    }

    @GetMapping("/exams/{examId}/question-accuracy-top")
    public ApiResponse<Map<String, Object>> questionAccuracyTop(
            @PathVariable("examId") String examId,
            @RequestParam(name = "top", defaultValue = "10") int top,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Role", required = false) String role,
            @RequestHeader(value = "X-Permissions", required = false) String permissions) {
        String operatorId = requireTeacherOrAdmin(userId, role, permissions, PermissionCodes.REPORT_QUESTION_ACCURACY_VIEW);
        return ApiResponse.ok(reportDomainService.questionAccuracyTop(examId, top, operatorId, role));
    }

    @GetMapping("/exams/{examId}/score-sheet")
    public ApiResponse<Map<String, Object>> scoreSheet(
            @PathVariable("examId") String examId,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "limit", required = false) Integer limit,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Role", required = false) String role,
            @RequestHeader(value = "X-Permissions", required = false) String permissions) {
        String operatorId = requireTeacherOrAdmin(userId, role, permissions, PermissionCodes.REPORT_SCORE_DISTRIBUTION_VIEW);
        return ApiResponse.ok(reportDomainService.scoreSheet(examId, keyword, limit, operatorId, role));
    }

    private String requireTeacherOrAdmin(String userId, String role, String permissions, String requiredPermission) {
        String safeUserId = RoleGuard.requireUserId(userId);
        RoleGuard.requireRole(role, "ADMIN", "TEACHER");
        RoleGuard.requirePermission(role, permissions, requiredPermission);
        return safeUserId;
    }
}
