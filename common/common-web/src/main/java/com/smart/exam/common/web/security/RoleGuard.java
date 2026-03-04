package com.smart.exam.common.web.security;

import com.smart.exam.common.core.error.BizException;
import com.smart.exam.common.core.error.ErrorCode;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

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

    public static String requirePermission(String role, String permissionHeader, String requiredPermissionCode) {
        String normalizedRole = requireRole(role, "ADMIN", "TEACHER", "STUDENT");
        if ("ADMIN".equals(normalizedRole)) {
            return normalizedRole;
        }

        String normalizedRequiredPermission = normalize(requiredPermissionCode);
        if (!StringUtils.hasText(normalizedRequiredPermission)) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "Required permission is missing");
        }

        Set<String> grantedPermissions = parsePermissionHeader(permissionHeader);
        if (grantedPermissions.contains(normalizedRequiredPermission)) {
            return normalizedRole;
        }
        throw new BizException(ErrorCode.FORBIDDEN, "Insufficient permission");
    }

    private static Set<String> parsePermissionHeader(String permissionHeader) {
        Set<String> permissions = new HashSet<>();
        if (!StringUtils.hasText(permissionHeader)) {
            return permissions;
        }
        String[] values = permissionHeader.split(",");
        for (String value : values) {
            String normalized = normalize(value);
            if (StringUtils.hasText(normalized)) {
                permissions.add(normalized);
            }
        }
        return permissions;
    }

    private static String normalize(String role) {
        return role == null ? "" : role.trim().toUpperCase(Locale.ROOT);
    }
}
