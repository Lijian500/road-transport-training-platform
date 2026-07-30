package me.lj.train.admin.support;

import com.mybatisflex.core.query.QueryWrapper;
import me.lj.train.admin.mapper.UserMapper;
import me.lj.train.admin.model.entity.UserEntity;
import me.lj.train.common.security.SecurityConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static me.lj.train.admin.model.table.UserTableDef.USER;

/**
 * 登录版本同步及授权缓存失效。
 */
@Component
public class AuthorizationCacheService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthorizationCacheService.class);
    private static final DefaultRedisScript<Long> SYNC_LOGIN_VERSION_SCRIPT =
            new DefaultRedisScript<Long>(
                    "local current = redis.call('get', KEYS[1]); "
                            + "if (not current) or (tonumber(ARGV[1]) > tonumber(current)) then "
                            + "redis.call('set', KEYS[1], ARGV[1]); return 1; end; return 0;",
                    Long.class);

    private final StringRedisTemplate redisTemplate;
    private final UserMapper userMapper;

    public AuthorizationCacheService(StringRedisTemplate redisTemplate, UserMapper userMapper) {
        this.redisTemplate = redisTemplate;
        this.userMapper = userMapper;
    }

    public void syncLoginVersion(UserEntity user) {
        Long userId = user.getId();
        long loginVersion = user.getLoginVersion();
        executeAfterCommit(() -> syncLoginVersionNow(userId, loginVersion));
    }

    public void invalidateAuthorization(Long userId) {
        executeAfterCommit(() ->
                redisTemplate.delete(SecurityConstants.REDIS_AUTHORIZATION_PREFIX + userId));
    }

    public void syncEnterpriseUsers(Long enterpriseId) {
        List<UserEntity> users = userMapper.selectListByQuery(
                QueryWrapper.create().where(USER.ENTERPRISE_ID.eq(enterpriseId)));
        List<Long> userIds = users.stream()
                .map(UserEntity::getId)
                .collect(Collectors.toList());
        executeAfterCommit(() -> {
            users.forEach(user -> syncLoginVersionNow(user.getId(), user.getLoginVersion()));
            deleteAuthorizationKeys(userIds);
        });
    }

    public void invalidateUsers(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        List<Long> normalizedUserIds = userIds.stream()
                .filter(userId -> userId != null)
                .distinct()
                .collect(Collectors.toList());
        executeAfterCommit(() -> deleteAuthorizationKeys(normalizedUserIds));
    }

    private void syncLoginVersionNow(Long userId, long loginVersion) {
        redisTemplate.execute(
                SYNC_LOGIN_VERSION_SCRIPT,
                Collections.singletonList(SecurityConstants.REDIS_LOGIN_VERSION_PREFIX + userId),
                String.valueOf(loginVersion));
    }

    private void deleteAuthorizationKeys(List<Long> userIds) {
        if (userIds.isEmpty()) {
            return;
        }
        redisTemplate.delete(userIds.stream()
                .map(userId -> SecurityConstants.REDIS_AUTHORIZATION_PREFIX + userId)
                .collect(Collectors.toList()));
    }

    /**
     * 数据库事务提交后再同步缓存，避免回滚事务提前暴露未提交状态。
     */
    private void executeAfterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    action.run();
                } catch (RuntimeException exception) {
                    LOGGER.error("事务已提交，但认证缓存同步失败", exception);
                }
            }
        });
    }
}
