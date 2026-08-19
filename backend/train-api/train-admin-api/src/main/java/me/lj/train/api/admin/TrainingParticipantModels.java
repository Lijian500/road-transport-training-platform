package me.lj.train.api.admin;

import java.io.Serializable;
import java.util.List;

/**
 * 培训计划选择学员所需的最小目录模型。
 */
public final class TrainingParticipantModels {

    private TrainingParticipantModels() {
    }

    public record ParticipantQuery(String keyword, Long orgId) implements Serializable {
    }

    public record ValidateParticipantsCommand(List<Long> userIds) implements Serializable {
    }

    public record ParticipantView(
            Long userId,
            Long enterpriseId,
            Long orgId,
            String orgName,
            String username,
            String displayName) implements Serializable {
    }
}
