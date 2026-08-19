package me.lj.train.api.admin;

import me.lj.train.api.admin.TrainingParticipantModels.ParticipantQuery;
import me.lj.train.api.admin.TrainingParticipantModels.ParticipantView;
import me.lj.train.api.admin.TrainingParticipantModels.ValidateParticipantsCommand;
import me.lj.train.common.core.result.Result;

import java.util.List;

/**
 * 培训服务使用的企业学员目录RPC接口。
 */
public interface TrainingParticipantService {

    Result<List<ParticipantView>> listCandidates(ParticipantQuery query);

    Result<List<ParticipantView>> validate(ValidateParticipantsCommand command);
}
