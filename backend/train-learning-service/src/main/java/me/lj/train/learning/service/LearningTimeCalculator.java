package me.lj.train.learning.service;

import me.lj.train.common.core.exception.BusinessException;
import me.lj.train.common.core.result.AppErrorCode;
import org.springframework.stereotype.Component;

/**
 * 无状态有效学时计算器，所有时间统一使用毫秒。
 */
@Component
public class LearningTimeCalculator {

    public Calculation calculate(
            long elapsedMillis,
            long previousPositionMillis,
            long reportedPositionMillis,
            long maximumGapMillis,
            long remainingDurationMillis,
            long toleranceMillis,
            boolean allowSeek) {
        if (elapsedMillis > maximumGapMillis) {
            return new Calculation(true, 0L);
        }
        long positionDelta = reportedPositionMillis - previousPositionMillis;
        if (!allowSeek && positionDelta > elapsedMillis + toleranceMillis) {
            throw new BusinessException(AppErrorCode.LEARNING_POSITION_INVALID,
                    "当前课程不允许拖动视频");
        }
        long credited = Math.min(
                Math.min(Math.max(0L, positionDelta), Math.max(0L, elapsedMillis)),
                Math.min(maximumGapMillis, Math.max(0L, remainingDurationMillis)));
        return new Calculation(false, credited);
    }

    public long completionPosition(long durationMillis, long toleranceMillis) {
        return Math.max(0L, durationMillis - toleranceMillis);
    }

    public record Calculation(boolean timedOut, long creditedDurationMillis) {
    }
}
