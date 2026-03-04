package com.smart.exam.common.web.security;

import com.smart.exam.common.core.error.BizException;
import com.smart.exam.common.core.error.ErrorCode;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Locale;

public final class RoleGuard {

    private RoleGuard() {
    }

    public static String requireUserId(String userId) {
        if (!StringUtils.hasText(userId)) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return userId.trim();
    }

    public static String requireRole(String role, String... allowedRoles) {
        if (!StringUtils.hasText(role)) {
            throw new BizException(ErrorCode.FORBIDDEN, "Role is required");
        }
        String normalizedRole = normalize(role);
        boolean allowed = Arrays.stream(allowedRoles)
                .map(RoleGuard::normalize)
                .anyMatch(normalizedRole::equals);
        if (!allowed) {
            throw new BizException(ErrorCode.FORBIDDEN, "Insufficient role");
        }
        return normalizedRole;
    }

    private static String normalize(String role) {
        return role == null ? "" : role.trim().toUpperCase(Locale.ROOT);
    }
}
