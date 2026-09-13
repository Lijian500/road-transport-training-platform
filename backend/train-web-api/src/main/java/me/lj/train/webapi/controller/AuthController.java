package me.lj.train.webapi.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import me.lj.train.common.core.result.Result;
import me.lj.train.api.admin.UserService;
import me.lj.train.api.admin.AdminModels.UserView;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.web.bind.annotation.PutMapping;
import me.lj.train.webapi.model.AuthSessionView;
import me.lj.train.webapi.security.SessionService;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.Map;

/**
 * 登录会话REST接口。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @DubboReference(check = false, retries = 0)
    private UserService userService;

    /** 查看本人资料，身份由登录上下文决定。 */
    @GetMapping("/profile")
    public Result<UserView> profile() {
        return userService.profile();
    }

    /** 修改本人基本资料。 */
    @PutMapping("/profile")
    public Result<UserView> updateProfile(
            @Valid @RequestBody ProfileRequest request) {
        return userService.updateProfile(request.displayName(), request.phone());
    }

    public record ProfileRequest(@NotBlank String displayName, String phone) { }

    private final SessionService sessionService;

    public AuthController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @GetMapping("/csrf")
    public Result<Map<String, String>> csrf(CsrfToken token) {
        return Result.ok(Collections.singletonMap("token", token.getToken()));
    }

    @PostMapping("/login")
    public Result<AuthSessionView> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response) {
        return Result.ok(sessionService.login(request.username(), request.password(), response));
    }

    @PostMapping("/refresh")
    public Result<AuthSessionView> refresh(
            HttpServletRequest request,
            HttpServletResponse response) {
        return Result.ok(sessionService.refresh(request, response));
    }

    @PostMapping("/logout")
    public Result<?> logout(HttpServletRequest request, HttpServletResponse response) {
        sessionService.logout(request, response);
        return Result.ok();
    }

    @GetMapping("/me")
    public Result<AuthSessionView> me() {
        return Result.ok(sessionService.current());
    }

    @PostMapping("/change-password")
    public Result<AuthSessionView> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            HttpServletResponse response) {
        return Result.ok(sessionService.changePassword(
                request.oldPassword(), request.newPassword(), response));
    }

    public record LoginRequest(
            @NotBlank(message = "用户名不能为空") String username,
            @NotBlank(message = "密码不能为空") String password) {
    }

    public record ChangePasswordRequest(
            @NotBlank(message = "原密码不能为空") String oldPassword,
            @NotBlank(message = "新密码不能为空") String newPassword) {
    }
}
