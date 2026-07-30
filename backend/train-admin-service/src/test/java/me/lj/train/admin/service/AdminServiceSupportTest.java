package me.lj.train.admin.service;

import me.lj.train.common.core.exception.BusinessException;
import me.lj.train.common.core.result.AppErrorCode;
import me.lj.train.common.core.result.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminServiceSupportTest {

    @Mock
    private PlatformTransactionManager transactionManager;
    @Mock
    private TransactionStatus transactionStatus;

    private TestAdminServiceSupport serviceSupport;

    @BeforeEach
    void setUp() {
        serviceSupport = new TestAdminServiceSupport(transactionManager);
    }

    @Test
    void shouldCommitTransactionalExecutionOnSuccess() {
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(transactionStatus);

        Result<String> result = serviceSupport.runTransactional(() -> "success");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEqualTo("success");
        InOrder inOrder = inOrder(transactionManager);
        inOrder.verify(transactionManager).getTransaction(any(TransactionDefinition.class));
        inOrder.verify(transactionManager).commit(transactionStatus);
        verify(transactionManager, never()).rollback(transactionStatus);
    }

    @Test
    void shouldCommitVoidTransactionalExecutionOnSuccess() {
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(transactionStatus);

        Result<?> result = serviceSupport.runVoidTransactional(() -> {
        });

        assertThat(result.isSuccess()).isTrue();
        verify(transactionManager).commit(transactionStatus);
        verify(transactionManager, never()).rollback(transactionStatus);
    }

    @Test
    void shouldRollbackBeforeReturningBusinessError() {
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(transactionStatus);

        Result<String> result = serviceSupport.runTransactional(() -> {
            throw new BusinessException(AppErrorCode.USER_NOT_FOUND, "指定用户不存在");
        });

        verify(transactionManager).rollback(transactionStatus);
        verify(transactionManager, never()).commit(transactionStatus);
        assertThat(result.getCode()).isEqualTo(AppErrorCode.USER_NOT_FOUND.getCode());
        assertThat(result.getMessage()).isEqualTo("指定用户不存在");
    }

    @Test
    void shouldRollbackAndReturnSystemErrorForRuntimeException() {
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(transactionStatus);

        Result<?> result = serviceSupport.runVoidTransactional(() -> {
            throw new IllegalStateException("unexpected");
        });

        verify(transactionManager).rollback(transactionStatus);
        verify(transactionManager, never()).commit(transactionStatus);
        assertThat(result.getCode()).isEqualTo(AppErrorCode.SYSTEM_ERROR.getCode());
        assertThat(result.getMessage()).isEqualTo(AppErrorCode.SYSTEM_ERROR.getMessage());
    }

    @Test
    void shouldNotStartTransactionForPlainExecution() {
        Result<String> result = serviceSupport.run(() -> "success");
        Result<?> voidResult = serviceSupport.runVoid(() -> {
        });

        assertThat(result.isSuccess()).isTrue();
        assertThat(voidResult.isSuccess()).isTrue();
        verifyNoInteractions(transactionManager);
    }

    private static class TestAdminServiceSupport extends AdminServiceSupport {

        private TestAdminServiceSupport(PlatformTransactionManager transactionManager) {
            super(transactionManager);
        }

        private <T> Result<T> run(Supplier<T> supplier) {
            return execute(supplier);
        }

        private Result<?> runVoid(Runnable runnable) {
            return executeVoid(runnable);
        }

        private <T> Result<T> runTransactional(Supplier<T> supplier) {
            return executeTransactional(supplier);
        }

        private Result<?> runVoidTransactional(Runnable runnable) {
            return executeVoidTransactional(runnable);
        }
    }
}
