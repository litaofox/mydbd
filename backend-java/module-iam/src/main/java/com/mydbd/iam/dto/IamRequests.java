package com.mydbd.iam.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * IAM 请求 DTO 集合（record）
 */
public final class IamRequests {

    private IamRequests() {
    }

    public record LoginRequest(
            @NotBlank String username,
            @NotBlank String password) {
    }

    public record MfaVerifyRequest(
            @NotBlank String mfaToken,
            @NotBlank String totpCode) {
    }

    public record ChangePasswordRequest(
            @NotBlank String oldPassword,
            @NotBlank String newPassword) {
    }

    public record ProfileUpdateRequest(
            String phone,
            String email) {
    }

    public record MfaEnableRequest(
            @NotBlank String totpCode) {
    }

    public record MfaDisableRequest(
            @NotBlank String password,
            @NotBlank String totpCode) {
    }

    /** 新建/编辑用户；编辑时 password 为空表示不改密码 */
    public record UserSaveRequest(
            String username,
            @NotBlank @Size(max = 40) String realName,
            String password,
            String phone,
            String email,
            Long deptId,
            Integer status,
            String remark,
            List<Long> roleIds) {
    }

    public record ResetPasswordRequest(
            @NotBlank String newPassword) {
    }

    public record AssignRolesRequest(
            @NotNull List<Long> roleIds) {
    }

    public record RoleSaveRequest(
            String roleCode,
            @NotBlank @Size(max = 40) String roleName,
            @NotNull Integer dataScope,
            Integer status,
            String remark,
            List<Long> menuIds,
            List<Long> deptIds) {
    }
}
