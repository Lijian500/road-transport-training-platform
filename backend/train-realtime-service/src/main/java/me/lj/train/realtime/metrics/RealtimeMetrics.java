package me.lj.train.realtime.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/** 实时学习连接、消息、错误和RPC指标。 */
@Component
public class RealtimeMetrics {

    private final MeterRegistry registry;
    private final Timer rpcTimer;
    private final AtomicInteger activeConnections = new AtomicInteger();

    public RealtimeMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.rpcTimer = registry.timer("train.realtime.rpc.duration");
        registry.gauge("train.realtime.connections.active", activeConnections);
    }

    public void connectionOpened() {
        activeConnections.incrementAndGet();
    }

    public void connectionClosed() {
        activeConnections.updateAndGet(value -> Math.max(0, value - 1));
    }

    public void message(String type) {
        registry.counter("train.realtime.messages", "type", type).increment();
    }

    public void protocolError() {
        registry.counter("train.realtime.protocol.errors").increment();
    }

    public void heartbeatTimeout() {
        registry.counter("train.realtime.heartbeat.timeouts").increment();
    }

    public void replacement() {
        registry.counter("train.realtime.connection.replacements").increment();
    }

    public void reconnect() {
        registry.counter("train.realtime.reconnects").increment();
    }

    public Timer.Sample startRpc() {
        return Timer.start(registry);
    }

    public void stopRpc(Timer.Sample sample) {
        sample.stop(rpcTimer);
    }
}
