import me.lj.train.learning.service.LearningTimeCalculator;

/** 使用真实计算器执行确定性学时实验；不代表网络或数据库性能。 */
public class LearningTimeExperiment {
    /** 输出可归档JSON，预期不符时失败，不把模拟输入描述为真人学习时长。 */
    public static void main(String[] args) {
        LearningTimeCalculator calculator = new LearningTimeCalculator();
        long steady = accumulated(calculator, false);
        long jitter = accumulated(calculator, true);
        long timedOut = calculator.calculate(31000, 0, 31000, 30000, 60000, 2000, false).creditedDurationMillis();
        long capped = calculator.calculate(10000, 0, 10000, 30000, 3000, 2000, false).creditedDurationMillis();
        if (steady != 100000 || jitter != 95000 || timedOut != 0 || capped != 3000) {
            throw new AssertionError("学时计算与预期边界不一致");
        }
        System.out.println("{\"scope\":\"deterministic calculator inputs, no network\",\"steadyMillis\":"
                + steady + ",\"jitterMillis\":" + jitter + ",\"jitterMediaMillis\":100000,\"timedOutMillis\":"
                + timedOut + ",\"remainingCapMillis\":" + capped + "}");
    }

    /** 模拟100段媒体每次前进1秒，抖动组服务端间隔在0.9秒和1.1秒间交替。 */
    private static long accumulated(LearningTimeCalculator calculator, boolean jitter) {
        long credited = 0;
        for (int index = 0; index < 100; index++) {
            long elapsed = jitter ? (index % 2 == 0 ? 900 : 1100) : 1000;
            credited += calculator.calculate(elapsed, index * 1000L, (index + 1) * 1000L,
                    30000, 300000 - credited, 2000, false).creditedDurationMillis();
        }
        return credited;
    }
}
