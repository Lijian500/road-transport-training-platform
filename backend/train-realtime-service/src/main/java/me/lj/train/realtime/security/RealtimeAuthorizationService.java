package me.lj.train.realtime.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.lj.train.api.admin.AdminAuthService;
import me.lj.train.api.admin.AdminModels.LoginAccount;
import me.lj.train.common.core.result.AppErrorCode;
import me.lj.train.common.core.result.Result;
import me.lj.train.common.security.SecurityConstants;
import me.lj.train.common.security.jwt.AccessTokenClaims;
import me.lj.train.common.security.model.LoginUser;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.Instant;

/** 从Redis及管理服务加载并校验实时学习授权。 */
@Component
public class RealtimeAuthorizationService {

    private static final Duration AUTHORIZATION_TTL = Duration.ofMinutes(10);
    private static final String STUDY_PERMISSION = "student:learning:study";

    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @DubboReference(check = false, timeout = 3000, retries = 0)
    private AdminAuthService adminAuthService;

    public RealtimeAuthorizationService(
            ReactiveStringRedisTemplate redisTemplate,
            ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /** 校验Token、登录版本及最新学习权限并构造完整用户。 */
    public Mono<LoginUser> authenticate(AccessTokenClaims claims) {
        if (claims == null || claims.getUserId() == null || claims.getExpiresAt() == null
                || claims.getSessionId() == null || claims.getSessionId().isBlank()
                || !claims.getExpiresAt().isAfter(Instant.now())) {
            return Mono.error(new RealtimeAuthException(AppErrorCode.TOKEN_EXPIRED));
        }
        String versionKey = SecurityConstants.REDIS_LOGIN_VERSION_PREFIX + claims.getUserId();
        return redisTemplate.opsForValue().get(versionKey)
                .switchIfEmpty(Mono.error(new RealtimeAuthException(AppErrorCode.TOKEN_EXPIRED)))
                .flatMap(version -> {
                    if (!String.valueOf(claims.getLoginVersion()).equals(version)) {
                        return Mono.error(new RealtimeAuthException(AppErrorCode.TOKEN_EXPIRED));
                    }
                    return loadAccount(claims.getUserId());
                })
                .map(account -> validateAndConvert(claims, account));
    }

    private Mono<LoginAccount> loadAccount(Long userId) {
        String key = SecurityConstants.REDIS_AUTHORIZATION_PREFIX + userId;
        return readCache(key).switchIfEmpty(Mono.defer(() -> loadRemote(key, userId)));
    }

    private Mono<LoginAccount> readCache(String key) {
        return redisTemplate.opsForValue().get(key)
                .flatMap(json -> {
                    try {
                        return Mono.just(objectMapper.readValue(json, LoginAccount.class));
                    } catch (JsonProcessingException exception) {
                        return redisTemplate.delete(key).then(Mono.empty());
                    }
                })
                .onErrorResume(exception -> Mono.empty());
    }

    private Mono<LoginAccount> loadRemote(String key, Long userId) {
        return Mono.fromCallable(() -> unwrap(adminAuthService.getAuthorization(userId)))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(account -> cache(key, account).thenReturn(account));
    }

    private Mono<Boolean> cache(String key, LoginAccount account) {
        try {
            return redisTemplate.opsForValue().set(
                    key, objectMapper.writeValueAsString(account), AUTHORIZATION_TTL)
                    .onErrorReturn(false);
        } catch (JsonProcessingException exception) {
            return Mono.just(false);
        }
    }

    private LoginAccount unwrap(Result<LoginAccount> result) {
        if (result == null || !result.isSuccess() || result.getData() == null) {
            AppErrorCode code = result == null
                    ? AppErrorCode.SYSTEM_ERROR : AppErrorCode.fromCode(result.getCode());
            throw new RealtimeAuthException(code);
        }
        return result.getData();
    }

    private LoginUser validateAndConvert(AccessTokenClaims claims, LoginAccount account) {
        if (!claims.getUserId().equals(account.userId())
                || claims.getLoginVersion() != account.loginVersion()) {
            throw new RealtimeAuthException(AppErrorCode.TOKEN_EXPIRED);
        }
        if (account.enterpriseId() == null || account.platformAdmin()
                || claims.getEnterpriseId() == null
                || !claims.getEnterpriseId().equals(account.enterpriseId())
                || account.permissions() == null
                || !account.permissions().contains(STUDY_PERMISSION)) {
            throw new RealtimeAuthException(AppErrorCode.FORBIDDEN);
        }
        if (account.mustChangePassword()) {
            throw new RealtimeAuthException(AppErrorCode.PASSWORD_CHANGE_REQUIRED);
        }
        LoginUser user = new LoginUser();
        user.setUserId(account.userId());
        user.setEnterpriseId(account.enterpriseId());
        user.setSessionId(claims.getSessionId());
        user.setUsername(account.username());
        user.setDisplayName(account.displayName());
        user.setEnterpriseName(account.enterpriseName());
        user.setLoginVersion(account.loginVersion());
        user.setPlatformAdmin(account.platformAdmin());
        user.setMustChangePassword(account.mustChangePassword());
        user.setRoles(account.roles());
        user.setPermissions(account.permissions());
        return user;
    }
}
