package me.lj.train.api.admin;

import me.lj.train.common.core.page.PageRequest;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理领域RPC请求及响应模型。
 */
public final class AdminModels {

    private AdminModels() {
    }

    public record LoginCommand(String username, String password) implements Serializable {
    }

    public record LoginAccount(
            Long userId,
            Long enterpriseId,
            String username,
            String displayName,
            String enterpriseName,
            long loginVersion,
            boolean platformAdmin,
            boolean mustChangePassword,
            List<String> roles,
            List<String> permissions) implements Serializable {
    }

    public record ChangePasswordCommand(
            Long userId,
            String oldPassword,
            String newPassword) implements Serializable {
    }

    public record EnterpriseQuery(
            int pageNumber,
            int pageSize,
            String keyword,
            String status) implements Serializable {

        public PageRequest toPageRequest() {
            return new PageRequest(pageNumber, pageSize);
        }
    }

    public record CreateEnterpriseCommand(
            String code,
            String name,
            String contactName,
            String contactPhone,
            String address,
            String adminUsername,
            String adminDisplayName,
            String adminPhone,
            String temporaryPassword) implements Serializable {
    }

    public record UpdateEnterpriseCommand(
            Long id,
            String name,
            String contactName,
            String contactPhone,
            String address) implements Serializable {
    }

    public record EnterpriseView(
            Long id,
            String code,
            String name,
            String contactName,
            String contactPhone,
            String address,
            String status,
            LocalDateTime createdAt) implements Serializable {
    }

    public static final class EnterpriseAdministratorView implements Serializable {

        private static final long serialVersionUID = 1L;

        private final Long id;
        private final String username;
        private final String displayName;
        private final String phone;
        private final String status;
        private final boolean mustChangePassword;
        private final LocalDateTime createdAt;

        public EnterpriseAdministratorView(
                Long id,
                String username,
                String displayName,
                String phone,
                String status,
                boolean mustChangePassword,
                LocalDateTime createdAt) {
            this.id = id;
            this.username = username;
            this.displayName = displayName;
            this.phone = phone;
            this.status = status;
            this.mustChangePassword = mustChangePassword;
            this.createdAt = createdAt;
        }

        public Long getId() {
            return id;
        }

        public String getUsername() {
            return username;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getPhone() {
            return phone;
        }

        public String getStatus() {
            return status;
        }

        public boolean isMustChangePassword() {
            return mustChangePassword;
        }

        public LocalDateTime getCreatedAt() {
            return createdAt;
        }
    }

    public static final class ResetEnterpriseAdministratorPasswordCommand implements Serializable {

        private static final long serialVersionUID = 1L;

        private final Long enterpriseId;
        private final Long userId;
        private final String temporaryPassword;

        public ResetEnterpriseAdministratorPasswordCommand(
                Long enterpriseId,
                Long userId,
                String temporaryPassword) {
            this.enterpriseId = enterpriseId;
            this.userId = userId;
            this.temporaryPassword = temporaryPassword;
        }

        public Long getEnterpriseId() {
            return enterpriseId;
        }

        public Long getUserId() {
            return userId;
        }

        public String getTemporaryPassword() {
            return temporaryPassword;
        }
    }

    public record ChangeStatusCommand(Long id, String status) implements Serializable {
    }

    public record CreateOrgCommand(
            Long parentId,
            String name,
            String code,
            int sortOrder) implements Serializable {
    }

    public record UpdateOrgCommand(
            Long id,
            Long parentId,
            String name,
            String code,
            int sortOrder) implements Serializable {
    }

    public record OrgView(
            Long id,
            Long parentId,
            String name,
            String code,
            String type,
            String status,
            int sortOrder,
            List<OrgView> children) implements Serializable {
    }

    public record UserQuery(
            int pageNumber,
            int pageSize,
            String keyword,
            Long orgId,
            String status) implements Serializable {

        public PageRequest toPageRequest() {
            return new PageRequest(pageNumber, pageSize);
        }
    }

    public record CreateUserCommand(
            String username,
            String displayName,
            String phone,
            Long orgId,
            String temporaryPassword,
            List<Long> roleIds) implements Serializable {
    }

    public record UpdateUserCommand(
            Long id,
            String displayName,
            String phone,
            Long orgId) implements Serializable {
    }

    public record ResetPasswordCommand(Long id, String temporaryPassword) implements Serializable {
    }

    public record AssignRolesCommand(Long userId, List<Long> roleIds) implements Serializable {
    }

    public record UserView(
            Long id,
            Long enterpriseId,
            Long orgId,
            String orgName,
            String username,
            String displayName,
            String phone,
            String status,
            boolean mustChangePassword,
            List<Long> roleIds,
            List<String> roleNames,
            LocalDateTime createdAt) implements Serializable {
    }

    public record RoleQuery(
            int pageNumber,
            int pageSize,
            String keyword,
            String status) implements Serializable {

        public PageRequest toPageRequest() {
            return new PageRequest(pageNumber, pageSize);
        }
    }

    public record CreateRoleCommand(
            String code,
            String name,
            String description) implements Serializable {
    }

    public record UpdateRoleCommand(
            Long id,
            String name,
            String description) implements Serializable {
    }

    public record AssignPermissionsCommand(
            Long roleId,
            List<Long> permissionIds) implements Serializable {
    }

    public record RoleView(
            Long id,
            String code,
            String name,
            String description,
            String status,
            boolean builtIn,
            List<Long> permissionIds,
            LocalDateTime createdAt) implements Serializable {
    }

    public record PermissionView(
            Long id,
            Long parentId,
            String code,
            String name,
            String type,
            String scope,
            int sortOrder,
            List<PermissionView> children) implements Serializable {
    }
}
