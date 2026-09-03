package me.lj.train.realtime.connection;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** 单实例内按用户、会话和浏览器实例维护唯一连接。 */
@Component
public class LearningConnectionRegistry {

    private final ConcurrentMap<ConnectionKey, LearningConnection> connections =
            new ConcurrentHashMap<ConnectionKey, LearningConnection>();
    private final ConcurrentMap<LearningConnection, ConnectionKey> keys =
            new ConcurrentHashMap<LearningConnection, ConnectionKey>();

    /** 注册最新连接，并通知同一绑定的旧连接退出。 */
    public LearningConnection register(LearningConnection connection) {
        ConnectionKey nextKey = keyOf(connection);
        ConnectionKey previousKey = keys.put(connection, nextKey);
        if (previousKey != null && !previousKey.equals(nextKey)) {
            connections.remove(previousKey, connection);
        }
        LearningConnection previous = connections.put(nextKey, connection);
        if (previous != null && previous != connection) {
            previous.requestReplacement();
        }
        return previous;
    }

    /** 仅移除仍指向当前对象的注册，避免旧连接清理掉新连接。 */
    public void remove(LearningConnection connection) {
        ConnectionKey key = keys.remove(connection);
        if (key != null) {
            connections.remove(key, connection);
        }
    }

    public int size() {
        return connections.size();
    }

    /** 查询指定企业、学员和学习会话的全部浏览器连接。 */
    public List<LearningConnection> find(
            Long enterpriseId,
            Long userId,
            Long sessionId) {
        return connections.values().stream()
                .filter(connection -> enterpriseId.equals(
                        connection.getLoginUser().getEnterpriseId()))
                .filter(connection -> userId.equals(
                        connection.getLoginUser().getUserId()))
                .filter(connection -> sessionId.equals(connection.getStudySessionId()))
                .toList();
    }

    private ConnectionKey keyOf(LearningConnection connection) {
        if (!connection.isBound()) {
            throw new IllegalStateException("连接尚未绑定学习会话");
        }
        return new ConnectionKey(
                connection.getLoginUser().getUserId(),
                connection.getStudySessionId(),
                connection.getClientInstanceId());
    }

    private record ConnectionKey(Long userId, Long sessionId, String clientInstanceId) {
    }
}
