package me.lj.train.training.service;

import me.lj.train.common.core.exception.BusinessException;
import me.lj.train.common.core.result.AppErrorCode;
import me.lj.train.common.core.result.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

/**
 * 培训Dubbo服务统一异常和事务转换。
 */
abstract class TrainingServiceSupport {

    private static final Logger LOGGER = LoggerFactory.getLogger(TrainingServiceSupport.class);
    private final TransactionTemplate transactionTemplate;

    protected TrainingServiceSupport(PlatformTransactionManager transactionManager) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    protected <T> Result<T> execute(Supplier<T> supplier) {
        try {
            return Result.ok(supplier.get());
        } catch (BusinessException exception) {
            return Result.failed(exception.getErrorCode(), exception.getMessage());
        } catch (RuntimeException exception) {
            LOGGER.error("培训Dubbo服务执行失败", exception);
            return Result.failed(AppErrorCode.SYSTEM_ERROR);
        }
    }

    protected Result<?> executeVoid(Runnable runnable) {
        try {
            runnable.run();
            return Result.ok();
        } catch (BusinessException exception) {
            return Result.failed(exception.getErrorCode(), exception.getMessage());
        } catch (RuntimeException exception) {
            LOGGER.error("培训Dubbo服务执行失败", exception);
            return Result.failed(AppErrorCode.SYSTEM_ERROR);
        }
    }

    protected <T> Result<T> executeTransactional(Supplier<T> supplier) {
        return execute(() -> transactionTemplate.execute(status -> supplier.get()));
    }

    protected Result<?> executeVoidTransactional(Runnable runnable) {
        return executeVoid(() -> transactionTemplate.executeWithoutResult(status -> runnable.run()));
    }

    protected <T> T runInTransaction(Supplier<T> supplier) {
        return transactionTemplate.execute(status -> supplier.get());
    }

    protected void runInTransaction(Runnable runnable) {
        transactionTemplate.executeWithoutResult(status -> runnable.run());
    }
}
