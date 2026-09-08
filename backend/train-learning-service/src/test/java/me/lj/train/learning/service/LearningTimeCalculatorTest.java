package me.lj.train.learning.service;

import me.lj.train.common.core.exception.BusinessException;
import me.lj.train.common.core.result.AppErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 有效学时计算核心规则测试。 */
class LearningTimeCalculatorTest {

    private final LearningTimeCalculator calculator = new LearningTimeCalculator();

    @Test
    void shouldUseSmallestTrustedDelta() {
        LearningTimeCalculator.Calculation result = calculator.calculate(
                20_000L, 10_000L, 28_000L, 30_000L, 60_000L, 2_000L, false);

        assertThat(result.timedOut()).isFalse();
        assertThat(result.creditedDurationMillis()).isEqualTo(18_000L);
    }

    @Test
    void shouldNotCreditTimeoutOrBackwardPlayback() {
        assertThat(calculator.calculate(
                31_000L, 10_000L, 30_000L, 30_000L, 60_000L, 2_000L, true)
                .timedOut()).isTrue();
        assertThat(calculator.calculate(
                10_000L, 20_000L, 5_000L, 30_000L, 60_000L, 2_000L, true)
                .creditedDurationMillis()).isZero();
    }

    @Test
    void shouldRejectForwardSeekWhenCourseDisallowsIt() {
        assertThatThrownBy(() -> calculator.calculate(
                5_000L, 10_000L, 30_000L, 30_000L, 60_000L, 2_000L, false))
                .isInstanceOf(BusinessException.class)
                .extracting(value -> ((BusinessException) value).getErrorCode())
                .isEqualTo(AppErrorCode.LEARNING_POSITION_INVALID);
    }

    @Test
    void shouldApplyToleranceToCoursewareCompletionPosition() {
        assertThat(calculator.completionPosition(60_000L, 5_000L)).isEqualTo(55_000L);
        assertThat(calculator.completionPosition(3_000L, 5_000L)).isZero();
    }

    /** 交替抖动会产生保守差额，容差不能被用于赠送丢失学时。 */
    @Test
    void shouldCreditNinetyFiveSecondsForHundredSecondsWithJitter() {
        long credited = 0L;
        for (int index = 0; index < 10; index++) {
            credited += calculator.calculate(index % 2 == 0 ? 9_000L : 11_000L,
                    index * 10_000L, (index + 1) * 10_000L,
                    30_000L, 100_000L - credited, 2_000L, false).creditedDurationMillis();
        }
        assertThat(credited).isEqualTo(95_000L);
    }
}
