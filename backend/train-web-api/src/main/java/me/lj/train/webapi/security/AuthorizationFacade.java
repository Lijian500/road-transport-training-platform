package me.lj.train.webapi.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.lj.train.api.admin.AdminAuthService;
import me.lj.train.api.admin.AdminModels.LoginAccount;
import me.lj.train.common.core.exception.BusinessException;
import me.lj.train.common.core.result.AppErrorCode;
import me.lj.train.common.security.SecurityConstants;
import me.lj.train.common.security.model.LoginUser;
import me.lj.train.webapi.support.RpcResultSupport;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 从Redis或管理服务加载最新授权快照。
 */
@Component
public class AuthorizationFacade {

    private static final Duration CACHE_TTL = Duration.ofMinutes(10);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @DubboReference(check = false, timeout = 3000, retries = 0)
    private AdminAuthService adminAuthService;

    public AuthorizationFacade(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public LoginAccount load(Long userId) {
        String key = SecurityConstants.REDIS_AUTHORIZATION_PREFIX + userId;
        String cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, LoginAccount.class);
            } catch (JsonProcessingException exception) {
                redisTemplate.delete(key);
            }
        }
        LoginAccount account = RpcResultSupport.unwrap(adminAuthService.getAuthorization(userId));
        cache(account);
        return account;
    }

    public void cache(LoginAccount account) {
        try {
            redisTemplate.opsForValue().set(
                    SecurityConstants.REDIS_AUTHORIZATION_PREFIX + account.userId(),
                    objectMapper.writeValueAsString(account),
                    CACHE_TTL);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(AppErrorCode.SYSTEM_ERROR, "授权信息序列化失败");
        }
    }

    public LoginUser toLoginUser(LoginAccount account, String sessionId) {
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(account.userId());
        loginUser.setEnterpriseId(account.enterpriseId());
        loginUser.setSessionId(sessionId);
        loginUser.setUsername(account.username());
        loginUser.setDisplayName(account.displayName());
        loginUser.setEnterpriseName(account.enterpriseName());
        loginUser.setLoginVersion(account.loginVersion());
        loginUser.setPlatformAdmin(account.platformAdmin());
        loginUser.setMustChangePassword(account.mustChangePassword());
        loginUser.setRoles(account.roles());
        loginUser.setPermissions(account.permissions());
        return loginUser;
    }
}
