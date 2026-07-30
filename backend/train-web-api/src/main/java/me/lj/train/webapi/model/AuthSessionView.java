package me.lj.train.webapi.model;

import java.util.List;

/**
 * 返回前端的当前会话信息，令牌仅通过安全Cookie传输。
 */
public record AuthSessionView(
        Long userId,
        Long enterpriseId,
        String username,
        String displayName,
        String enterpriseName,
        boolean platformAdmin,
        boolean mustChangePassword,
        List<String> roles,
        List<String> permissions,
        List<String> workspaces,
        String defaultWorkspace) {
}
