package me.lj.train.admin.support;

import com.mybatisflex.core.query.QueryWrapper;
import me.lj.train.admin.mapper.UserMapper;
import me.lj.train.admin.model.entity.UserEntity;
import me.lj.train.common.security.SecurityConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;

import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthorizationCacheServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private UserMapper userMapper;

    private AuthorizationCacheService cacheService;

    @BeforeEach
    void setUp() {
        cacheService = new AuthorizationCacheService(redisTemplate, userMapper);
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clear();
    }

    @Test
    void shouldSynchronizeLoginVersionAtomicallyOnlyAfterCommit() {
        beginTransactionSynchronization();
        UserEntity user = user(10L, 3L);

        cacheService.syncLoginVersion(user);

        verifyNoInteractions(redisTemplate);
        TransactionSynchronizationUtils.triggerAfterCommit();

        @SuppressWarnings({"rawtypes", "unchecked"})
        ArgumentCaptor<RedisScript<Long>> scriptCaptor =
                ArgumentCaptor.forClass((Class) RedisScript.class);
        verify(redisTemplate).execute(
                scriptCaptor.capture(),
                eq(Collections.singletonList(SecurityConstants.REDIS_LOGIN_VERSION_PREFIX + 10L)),
                eq("3"));
        assertThat(scriptCaptor.getValue().getResultType()).isEqualTo(Long.class);
        assertThat(scriptCaptor.getValue().getScriptAsString())
                .contains("tonumber(ARGV[1]) > tonumber(current)")
                .contains("redis.call('set', KEYS[1], ARGV[1])")
                .contains("return 0");
    }

    @Test
    void shouldNotAccessRedisWhenTransactionRollsBack() {
        beginTransactionSynchronization();

        cacheService.syncLoginVersion(user(10L, 3L));
        TransactionSynchronizationUtils.triggerAfterCompletion(
                TransactionSynchronization.STATUS_ROLLED_BACK);

        verifyNoInteractions(redisTemplate);
    }

    @Test
    void shouldLoadEnterpriseUsersOnceAndRefreshCachesAfterCommit() {
        beginTransactionSynchronization();
        UserEntity first = user(11L, 2L);
        UserEntity second = user(12L, 5L);
        when(userMapper.selectListByQuery(any(QueryWrapper.class)))
                .thenReturn(Arrays.asList(first, second));

        cacheService.syncEnterpriseUsers(20L);

        verify(userMapper).selectListByQuery(any(QueryWrapper.class));
        verifyNoMoreInteractions(userMapper);
        verifyNoInteractions(redisTemplate);
        TransactionSynchronizationUtils.triggerAfterCommit();

        verifyLoginVersionSync(11L, 2L);
        verifyLoginVersionSync(12L, 5L);
        verify(redisTemplate, times(1)).delete(Arrays.asList(
                SecurityConstants.REDIS_AUTHORIZATION_PREFIX + 11L,
                SecurityConstants.REDIS_AUTHORIZATION_PREFIX + 12L));
    }

    private void beginTransactionSynchronization() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
    }

    private void verifyLoginVersionSync(Long userId, long loginVersion) {
        verify(redisTemplate).execute(
                ArgumentMatchers.<RedisScript<Long>>any(),
                eq(Collections.singletonList(SecurityConstants.REDIS_LOGIN_VERSION_PREFIX + userId)),
                eq(String.valueOf(loginVersion)));
    }

    private UserEntity user(Long userId, long loginVersion) {
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setEnterpriseId(20L);
        user.setLoginVersion(loginVersion);
        return user;
    }
}
