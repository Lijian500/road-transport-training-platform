package me.lj.train.webapi.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import me.lj.train.api.admin.AdminModels.LoginAccount;
import me.lj.train.common.core.exception.BusinessException;
import me.lj.train.common.core.result.AppErrorCode;
import me.lj.train.common.core.result.Result;
import me.lj.train.common.security.SecurityConstants;
import me.lj.train.common.security.context.UserContext;
import me.lj.train.common.security.model.LoginUser;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * 将Gateway写入的可信用户头转换为BFF用户上下文。
 */
@Component
public class TrustedUserContextFilter extends OncePerRequestFilter {

    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/api/auth/csrf",
            "/api/auth/login",
            "/api/auth/refresh",
            "/api/auth/logout");

    private final AuthorizationFacade authorizationFacade;
    private final ObjectMapper objectMapper;

    public TrustedUserContextFilter(
            AuthorizationFacade authorizationFacade,
            ObjectMapper objectMapper) {
        this.authorizationFacade = authorizationFacade;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return PUBLIC_PATHS.contains(request.getRequestURI())
                || request.getRequestURI().startsWith("/actuator/")
                || !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        try {
            String userIdHeader = request.getHeader(SecurityConstants.HEADER_USER_ID);
            String versionHeader = request.getHeader(SecurityConstants.HEADER_LOGIN_VERSION);
            if (userIdHeader == null || versionHeader == null) {
                throw new BusinessException(AppErrorCode.UNAUTHORIZED);
            }
            LoginAccount account = authorizationFacade.load(Long.valueOf(userIdHeader));
            if (account.loginVersion() != Long.parseLong(versionHeader)) {
                throw new BusinessException(AppErrorCode.TOKEN_EXPIRED);
            }
            LoginUser loginUser = authorizationFacade.toLoginUser(
                    account, request.getHeader(SecurityConstants.HEADER_SESSION_ID));
            UserContext.set(loginUser);
            if (loginUser.isMustChangePassword()
                    && !"/api/auth/me".equals(request.getRequestURI())
                    && !"/api/auth/change-password".equals(request.getRequestURI())) {
                throw new BusinessException(AppErrorCode.PASSWORD_CHANGE_REQUIRED);
            }
            filterChain.doFilter(request, response);
        } catch (BusinessException | IllegalArgumentException exception) {
            AppErrorCode errorCode = exception instanceof BusinessException businessException
                    ? (AppErrorCode) businessException.getErrorCode()
                    : AppErrorCode.UNAUTHORIZED;
            String message = exception.getMessage() == null ? errorCode.getMessage() : exception.getMessage();
            response.setStatus(errorCode.getHttpStatus());
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getWriter(), Result.failed(errorCode, message));
        } finally {
            UserContext.clear();
        }
    }
}
