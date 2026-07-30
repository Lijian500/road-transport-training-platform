package me.lj.train.admin.service;

import me.lj.train.common.core.exception.BusinessException;
import me.lj.train.common.core.result.AppErrorCode;
import me.lj.train.common.core.result.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

/**
 * Dubbo服务统一业务异常转换。
 */
abstract class AdminServiceSupport {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdminServiceSupport.class);

    private final TransactionTemplate transactionTemplate;

    protected AdminServiceSupport(PlatformTransactionManager transactionManager) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    protected <T> Result<T> execute(Supplier<T> supplier) {
        try {
            return Result.ok(supplier.get());
        } catch (BusinessException exception) {
            return Result.failed(exception.getErrorCode(), exception.getMessage());
        } catch (RuntimeException exception) {
            LOGGER.error("Dubbo服务执行失败", exception);
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
            LOGGER.error("Dubbo服务执行失败", exception);
            return Result.failed(AppErrorCode.SYSTEM_ERROR);
        }
    }

    /**
     * 在事务完成提交或回滚后统一转换执行结果。
     */
    protected <T> Result<T> executeTransactional(Supplier<T> supplier) {
        return execute(() -> transactionTemplate.execute(status -> supplier.get()));
    }

    /**
     * 在事务完成提交或回滚后统一转换无返回值执行结果。
     */
    protected Result<?> executeVoidTransactional(Runnable runnable) {
        return executeVoid(() -> transactionTemplate.executeWithoutResult(status -> runnable.run()));
    }

    /**
     * 执行不转换异常的内部事务，供启动初始化等非RPC流程使用。
     */
    protected void runInTransaction(Runnable runnable) {
        transactionTemplate.executeWithoutResult(status -> runnable.run());
    }
}
