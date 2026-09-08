package me.lj.train.api.training;

import me.lj.train.api.training.TrainingRecordModels.*;
import me.lj.train.common.core.page.PageResult;
import me.lj.train.common.core.result.Result;

/** 培训档案只读RPC，学员和管理员入口分别执行授权。 */
public interface TrainingRecordService {
    /** 分页查询当前学员的已发布培训任务。 */
    Result<PageResult<TrainingRecordView>> pageMyRecords(RecordQuery query);
    /** 查询当前学员任务及冻结课程。 */
    Result<TrainingRecordView> getMyRecord(Long taskId);
    /** 查询本企业参训人员档案。 */
    Result<TrainingRecordView> getAdminRecord(Long taskId);
    /** 查询当前学员首页概览。 */
    Result<StudentOverviewView> myOverview();
}
