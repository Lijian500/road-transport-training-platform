package me.lj.train.learning.support;

import me.lj.train.common.core.exception.BusinessException;
import me.lj.train.common.core.result.AppErrorCode;
import me.lj.train.common.core.result.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

/**
 * 学习RPC统一异常和事务转换。
 */
public abstract class LearningServiceSupport {

    private static final Logger LOGGER = LoggerFactory.getLogger(LearningServiceSupport.class);
    private final TransactionTemplate transactionTemplate;

    protected LearningServiceSupport(PlatformTransactionManager transactionManager) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    protected <T> Result<T> execute(Supplier<T> supplier) {
        try {
            return Result.ok(supplier.get());
        } catch (BusinessException exception) {
            return Result.failed(exception.getErrorCode(), exception.getMessage());
        } catch (RuntimeException exception) {
            LOGGER.error("学习Dubbo服务执行失败", exception);
            return Result.failed(AppErrorCode.SYSTEM_ERROR);
        }
    }

    protected <T> Result<T> executeTransactional(Supplier<T> supplier) {
        return execute(() -> transactionTemplate.execute(status -> supplier.get()));
    }

    protected Result<?> executeVoidTransactional(Runnable runnable) {
        return executeTransactional(() -> {
            runnable.run();
            return null;
        });
    }

    protected void runInTransaction(Runnable runnable) {
        transactionTemplate.executeWithoutResult(status -> runnable.run());
    }
}
