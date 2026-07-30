package me.lj.train.webapi.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import me.lj.train.api.admin.AdminAuthService;
import me.lj.train.api.admin.AdminModels.ChangePasswordCommand;
import me.lj.train.api.admin.AdminModels.LoginAccount;
import me.lj.train.api.admin.AdminModels.LoginCommand;
import me.lj.train.common.core.exception.BusinessException;
import me.lj.train.common.core.result.AppErrorCode;
import me.lj.train.common.security.SecurityConstants;
import me.lj.train.common.security.context.UserContext;
import me.lj.train.common.security.jwt.JwtTokenService;
import me.lj.train.common.security.jwt.RefreshTokenGenerator;
import me.lj.train.common.security.model.LoginUser;
import me.lj.train.webapi.config.WebApiSecurityProperties;
import me.lj.train.webapi.model.AuthSessionView;
import me.lj.train.webapi.support.RpcResultSupport;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Access/Refresh Token签发、轮换与撤销。
 */
@Component
public class SessionService {

    private static final String FIELD_DIGEST = "digest";
    private static final String FIELD_USER_ID = "userId";
    private static final String FIELD_LOGIN_VERSION = "loginVersion";
    private static final DefaultRedisScript<Long> ROTATE_SCRIPT = new DefaultRedisScript<Long>(
            """
            local digest = redis.call('HGET', KEYS[1], 'digest')
            if not digest or digest ~= ARGV[1] then
              return 0
            end
            redis.call('HSET', KEYS[1], 'digest', ARGV[2])
            redis.call('EXPIRE', KEYS[1], ARGV[3])
            return 1
            """,
            Long.class);

    private final StringRedisTemplate redisTemplate;
    private final JwtTokenService jwtTokenService;
    private final WebApiSecurityProperties properties;
    private final AuthorizationFacade authorizationFacade;

    @DubboReference(check = false, timeout = 3000, retries = 0)
    private AdminAuthService adminAuthService;

    public SessionService(
            StringRedisTemplate redisTemplate,
            JwtTokenService jwtTokenService,
            WebApiSecurityProperties properties,
            AuthorizationFacade authorizationFacade) {
        this.redisTemplate = redisTemplate;
        this.jwtTokenService = jwtTokenService;
        this.properties = properties;
        this.authorizationFacade = authorizationFacade;
    }

    public AuthSessionView login(
            String username,
            String password,
            HttpServletResponse response) {
        LoginAccount account = RpcResultSupport.unwrap(
                adminAuthService.authenticate(new LoginCommand(username, password)));
        return createSession(account, response);
    }

    public AuthSessionView refresh(HttpServletRequest request, HttpServletResponse response) {
        RefreshCredential credential = readRefreshCredential(request);
        String key = refreshKey(credential.sessionId());
        Object userIdValue = redisTemplate.opsForHash().get(key, FIELD_USER_ID);
        Object versionValue = redisTemplate.opsForHash().get(key, FIELD_LOGIN_VERSION);
        if (userIdValue == null || versionValue == null) {
            throw new BusinessException(AppErrorCode.REFRESH_TOKEN_INVALID);
        }

        String nextToken = RefreshTokenGenerator.generate();
        long ttlSeconds = refreshTtl().toSeconds();
        Long rotated = redisTemplate.execute(
                ROTATE_SCRIPT,
                List.of(key),
                RefreshTokenGenerator.digest(credential.token()),
                RefreshTokenGenerator.digest(nextToken),
                String.valueOf(ttlSeconds));
        if (rotated == null || rotated.longValue() != 1L) {
            redisTemplate.delete(key);
            throw new BusinessException(AppErrorCode.REFRESH_TOKEN_INVALID);
        }

        LoginAccount account;
        try {
            account = RpcResultSupport.unwrap(
                    adminAuthService.getAuthorization(Long.valueOf(userIdValue.toString())));
        } catch (RuntimeException exception) {
            redisTemplate.delete(key);
            throw exception;
        }
        if (account.loginVersion() != Long.parseLong(versionValue.toString())) {
            redisTemplate.delete(key);
            throw new BusinessException(AppErrorCode.REFRESH_TOKEN_INVALID);
        }
        authorizationFacade.cache(account);
        issueCookies(account, credential.sessionId(), nextToken, response);
        return toView(account);
    }

    public void logout(HttpServletRequest request, HttpServletResponse response) {
        findCookie(request, SecurityConstants.REFRESH_TOKEN_COOKIE)
                .map(this::parseRefreshCredential)
                .ifPresent(credential -> redisTemplate.delete(refreshKey(credential.sessionId())));
        clearCookies(response);
    }

    public AuthSessionView current() {
        LoginUser loginUser = UserContext.require();
        LoginAccount account = new LoginAccount(
                loginUser.getUserId(),
                loginUser.getEnterpriseId(),
                loginUser.getUsername(),
                loginUser.getDisplayName(),
                loginUser.getEnterpriseName(),
                loginUser.getLoginVersion(),
                loginUser.isPlatformAdmin(),
                loginUser.isMustChangePassword(),
                loginUser.getRoles(),
                loginUser.getPermissions());
        return toView(account);
    }

    public AuthSessionView changePassword(
            String oldPassword,
            String newPassword,
            HttpServletResponse response) {
        LoginUser currentUser = UserContext.require();
        LoginAccount account = RpcResultSupport.unwrap(adminAuthService.changePassword(
                new ChangePasswordCommand(currentUser.getUserId(), oldPassword, newPassword)));
        if (currentUser.getSessionId() != null && !currentUser.getSessionId().isEmpty()) {
            redisTemplate.delete(refreshKey(currentUser.getSessionId()));
        }
        return createSession(account, response);
    }

    private AuthSessionView createSession(LoginAccount account, HttpServletResponse response) {
        String sessionId = UUID.randomUUID().toString();
        String refreshToken = RefreshTokenGenerator.generate();
        Map<String, String> values = new HashMap<String, String>();
        values.put(FIELD_DIGEST, RefreshTokenGenerator.digest(refreshToken));
        values.put(FIELD_USER_ID, String.valueOf(account.userId()));
        values.put(FIELD_LOGIN_VERSION, String.valueOf(account.loginVersion()));
        String key = refreshKey(sessionId);
        redisTemplate.opsForHash().putAll(key, values);
        redisTemplate.expire(key, refreshTtl());
        authorizationFacade.cache(account);
        issueCookies(account, sessionId, refreshToken, response);
        return toView(account);
    }

    private void issueCookies(
            LoginAccount account,
            String sessionId,
            String refreshToken,
            HttpServletResponse response) {
        LoginUser loginUser = authorizationFacade.toLoginUser(account, sessionId);
        Duration accessTtl = Duration.ofMinutes(properties.getAccessTokenMinutes());
        String accessToken = jwtTokenService.createAccessToken(loginUser, sessionId, accessTtl);
        addCookie(
                response,
                SecurityConstants.ACCESS_TOKEN_COOKIE,
                accessToken,
                "/",
                accessTtl);
        addCookie(
                response,
                SecurityConstants.REFRESH_TOKEN_COOKIE,
                sessionId + "." + refreshToken,
                "/api/auth",
                refreshTtl());
    }

    private void addCookie(
            HttpServletResponse response,
            String name,
            String value,
            String path,
            Duration maxAge) {
        ResponseCookie cookie = ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(properties.isSecureCookie())
                .sameSite("Lax")
                .path(path)
                .maxAge(maxAge)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearCookies(HttpServletResponse response) {
        addCookie(response, SecurityConstants.ACCESS_TOKEN_COOKIE, "", "/", Duration.ZERO);
        addCookie(response, SecurityConstants.REFRESH_TOKEN_COOKIE, "", "/api/auth", Duration.ZERO);
    }

    private RefreshCredential readRefreshCredential(HttpServletRequest request) {
        return findCookie(request, SecurityConstants.REFRESH_TOKEN_COOKIE)
                .map(this::parseRefreshCredential)
                .orElseThrow(() -> new BusinessException(AppErrorCode.REFRESH_TOKEN_INVALID));
    }

    private RefreshCredential parseRefreshCredential(String value) {
        String[] parts = value.split("\\.", 2);
        if (parts.length != 2 || parts[0].isEmpty() || parts[1].isEmpty()) {
            throw new BusinessException(AppErrorCode.REFRESH_TOKEN_INVALID);
        }
        return new RefreshCredential(parts[0], parts[1]);
    }

    private java.util.Optional<String> findCookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) {
            return java.util.Optional.empty();
        }
        return Arrays.stream(request.getCookies())
                .filter(cookie -> name.equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst();
    }

    private String refreshKey(String sessionId) {
        return SecurityConstants.REDIS_REFRESH_PREFIX + sessionId;
    }

    private Duration refreshTtl() {
        return Duration.ofDays(properties.getRefreshTokenDays());
    }

    private AuthSessionView toView(LoginAccount account) {
        List<String> workspaces = new ArrayList<String>();
        if (account.platformAdmin() || account.permissions().stream().anyMatch(code -> code.startsWith("admin:"))) {
            workspaces.add("admin");
        }
        if (!account.platformAdmin() && account.permissions().contains("student:workspace:view")) {
            workspaces.add("student");
        }
        String defaultWorkspace = workspaces.contains("admin") ? "admin" : "student";
        return new AuthSessionView(
                account.userId(),
                account.enterpriseId(),
                account.username(),
                account.displayName(),
                account.enterpriseName(),
                account.platformAdmin(),
                account.mustChangePassword(),
                account.roles(),
                account.permissions(),
                workspaces,
                defaultWorkspace);
    }

    private record RefreshCredential(String sessionId, String token) {
    }
}
