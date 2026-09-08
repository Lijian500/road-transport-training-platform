package me.lj.train.api.learning;

import me.lj.train.api.learning.LearningRecordModels.*;
import me.lj.train.api.learning.LearningStatisticsModels.TaskDurationView;
import me.lj.train.common.core.page.PageResult;
import me.lj.train.common.core.result.Result;
import java.util.List;

/** 学习档案和监管查询，所有查询均按服务端登录上下文限定范围。 */
public interface LearningRecordService {
    /** 批量读取当前学员任务学时。 */
    Result<List<TaskDurationView>> myDurations(List<Long> taskIds);
    /** 当前学员课程进度。 */
    Result<List<CourseRecordView>> myCourses(Long taskId);
    /** 本企业参训任务课程进度。 */
    Result<List<CourseRecordView>> adminCourses(Long taskId);
    /** 当前学员会话分页。 */
    Result<PageResult<SessionRecordView>> mySessions(SessionQuery query);
    /** 本企业参训任务会话分页。 */
    Result<PageResult<SessionRecordView>> adminSessions(SessionQuery query);
    /** 当前学员会话事件分页。 */
    Result<PageResult<EventRecordView>> myEvents(Long sessionId, int pageNumber, int pageSize);
    /** 本企业会话事件分页。 */
    Result<PageResult<EventRecordView>> adminEvents(Long sessionId, int pageNumber, int pageSize);
    /** 当前学员会话抽验任务分页。 */
    Result<PageResult<FaceRecordView>> myFaceChecks(Long sessionId, int pageNumber, int pageSize);
    /** 本企业会话抽验任务分页。 */
    Result<PageResult<FaceRecordView>> adminFaceChecks(Long sessionId, int pageNumber, int pageSize);
}
