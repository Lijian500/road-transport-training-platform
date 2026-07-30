package me.lj.train.common.core.util;

/**
 * 单机雪花ID生成器，论文部署环境使用固定节点编号。
 */
public final class IdGenerator {

    private static final long EPOCH = 1735689600000L;
    private static final long SEQUENCE_MASK = 4095L;

    private static long lastTimestamp = -1L;
    private static long sequence;

    private IdGenerator() {
    }

    public static synchronized long nextId() {
        long timestamp = System.currentTimeMillis();
        if (timestamp < lastTimestamp) {
            timestamp = lastTimestamp;
        }
        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & SEQUENCE_MASK;
            if (sequence == 0) {
                timestamp = waitNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }
        lastTimestamp = timestamp;
        return ((timestamp - EPOCH) << 12) | sequence;
    }

    private static long waitNextMillis(long currentTimestamp) {
        long timestamp = System.currentTimeMillis();
        while (timestamp <= currentTimestamp) {
            timestamp = System.currentTimeMillis();
        }
        return timestamp;
    }
}
