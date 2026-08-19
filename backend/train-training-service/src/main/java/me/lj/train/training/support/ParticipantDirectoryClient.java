package me.lj.train.training.support;

import me.lj.train.api.admin.TrainingParticipantModels.ParticipantQuery;
import me.lj.train.api.admin.TrainingParticipantModels.ParticipantView;
import me.lj.train.api.admin.TrainingParticipantModels.ValidateParticipantsCommand;
import me.lj.train.api.admin.TrainingParticipantService;
import me.lj.train.common.core.exception.BusinessException;
import me.lj.train.common.core.result.AppErrorCode;
import me.lj.train.common.core.result.Result;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 将管理服务的学员目录RPC转换为培训领域可直接使用的数据。
 */
@Component
public class ParticipantDirectoryClient {

    @DubboReference(check = false, timeout = 5000, retries = 0)
    private TrainingParticipantService participantService;

    public List<ParticipantView> listCandidates(String keyword, Long orgId) {
        return unwrap(participantService.listCandidates(new ParticipantQuery(keyword, orgId)));
    }

    public List<ParticipantView> validate(List<Long> userIds) {
        return unwrap(participantService.validate(new ValidateParticipantsCommand(userIds)));
    }

    private <T> T unwrap(Result<T> result) {
        if (result == null) {
            throw new BusinessException(AppErrorCode.SYSTEM_ERROR, "学员目录服务未返回结果");
        }
        if (!result.isSuccess()) {
            throw new BusinessException(AppErrorCode.fromCode(result.getCode()), result.getMessage());
        }
        return result.getData();
    }
}
