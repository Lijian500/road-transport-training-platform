package me.lj.train.realtime.rpc;

import io.micrometer.core.instrument.Timer;
import me.lj.train.api.learning.LearningModels.BindSessionCommand;
import me.lj.train.api.learning.LearningModels.LearningEventResultView;
import me.lj.train.api.learning.LearningModels.LearningSessionView;
import me.lj.train.api.learning.LearningModels.SubmitEventCommand;
import me.lj.train.api.learning.LearningSessionService;
import me.lj.train.common.core.result.AppErrorCode;
import me.lj.train.common.core.result.Result;
import me.lj.train.common.security.context.UserContext;
import me.lj.train.common.security.model.LoginUser;
import me.lj.train.realtime.metrics.RealtimeMetrics;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.function.Supplier;

/** 在阻塞线程中安全传播用户上下文并调用学习RPC。 */
@Component
public class LearningRpcClient {

    private final RealtimeMetrics metrics;

    @DubboReference(check = false, timeout = 10000, retries = 0)
    private LearningSessionService learningSessionService;

    public LearningRpcClient(RealtimeMetrics metrics) {
        this.metrics = metrics;
    }

    public Mono<LearningSessionView> bind(
            LoginUser user,
            Long sessionId,
            String clientInstanceId) {
        return invoke(user, () -> learningSessionService.bindSession(
                new BindSessionCommand(sessionId, clientInstanceId)));
    }

    public Mono<LearningEventResultView> submit(
            LoginUser user,
            SubmitEventCommand command) {
        return invoke(user, () -> learningSessionService.submitEvent(command));
    }

    private <T> Mono<T> invoke(LoginUser user, Supplier<Result<T>> invocation) {
        return Mono.fromCallable(() -> {
            Timer.Sample sample = metrics.startRpc();
            UserContext.set(user);
            try {
                return unwrap(invocation.get());
            } finally {
                UserContext.clear();
                metrics.stopRpc(sample);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private <T> T unwrap(Result<T> result) {
        if (result == null) {
            throw new RealtimeRpcException(
                    AppErrorCode.SYSTEM_ERROR.getCode(), "远程学习服务未返回结果");
        }
        if (!result.isSuccess()) {
            throw new RealtimeRpcException(result.getCode(), result.getMessage());
        }
        return result.getData();
    }
}
