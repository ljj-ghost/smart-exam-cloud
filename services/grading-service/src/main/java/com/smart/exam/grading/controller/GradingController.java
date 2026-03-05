package com.smart.exam.grading.controller;

import com.smart.exam.common.core.model.ApiResponse;
import com.smart.exam.common.web.security.PermissionCodes;
import com.smart.exam.common.web.security.RoleGuard;
import com.smart.exam.grading.dto.ManualScoreRequest;
import com.smart.exam.grading.model.GradingTask;
import com.smart.exam.grading.service.GradingDomainService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;

@RestController
@RequestMapping("/api/v1/grading")
public class GradingController {

    private final GradingDomainService gradingDomainService;

    public GradingController(GradingDomainService gradingDomainService) {
        this.gradingDomainService = gradingDomainService;
    }

    @GetMapping("/tasks")
    public ApiResponse<Collection<GradingTask>> listTasks(
            @RequestParam(name = "status", required = false) String status,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Role", required = false) String role,
            @RequestHeader(value = "X-Permissions", required = false) String permissions) {
        String operatorId = requireTeacherOrAdmin(userId, role, permissions, PermissionCodes.GRADING_TASK_VIEW);
        return ApiResponse.ok(gradingDomainService.listTasks(status, operatorId, role));
    }

    @PostMapping("/tasks/{taskId}/manual-score")
    public ApiResponse<GradingTask> manualScore(
            @PathVariable("taskId") String taskId,
            @Valid @RequestBody ManualScoreRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String graderId,
            @RequestHeader(value = "X-Role", required = false) String role,
            @RequestHeader(value = "X-Permissions", required = false) String permissions) {
        String safeGraderId = requireTeacherOrAdmin(
                graderId,
                role,
                permissions,
                PermissionCodes.GRADING_MANUAL_SCORE
        );
        return ApiResponse.ok(gradingDomainService.manualScore(taskId, request, safeGraderId, role));
    }

    private String requireTeacherOrAdmin(String userId, String role, String permissions, String requiredPermission) {
        String safeUserId = RoleGuard.requireUserId(userId);
        RoleGuard.requireRole(role, "ADMIN", "TEACHER");
        RoleGuard.requirePermission(role, permissions, requiredPermission);
        return safeUserId;
    }
}
