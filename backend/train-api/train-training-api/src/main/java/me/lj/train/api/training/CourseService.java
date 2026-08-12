package me.lj.train.api.training;

import me.lj.train.api.training.CourseModels.ChangeCourseStatusCommand;
import me.lj.train.api.training.CourseModels.CourseQuery;
import me.lj.train.api.training.CourseModels.CourseView;
import me.lj.train.api.training.CourseModels.CoursewareView;
import me.lj.train.api.training.CourseModels.CreateCourseCommand;
import me.lj.train.api.training.CourseModels.DeleteCoursewareCommand;
import me.lj.train.api.training.CourseModels.ReorderCoursewaresCommand;
import me.lj.train.api.training.CourseModels.UpdateCourseCommand;
import me.lj.train.api.training.CourseModels.UpdateCoursewareCommand;
import me.lj.train.common.core.page.PageResult;
import me.lj.train.common.core.result.Result;

/**
 * 企业课程与课件管理RPC接口。
 */
public interface CourseService {

    Result<PageResult<CourseView>> page(CourseQuery query);

    Result<CourseView> create(CreateCourseCommand command);

    Result<CourseView> get(Long id);

    Result<CourseView> update(UpdateCourseCommand command);

    Result<?> delete(Long id);

    Result<CourseView> changeStatus(ChangeCourseStatusCommand command);

    Result<CoursewareView> updateCourseware(UpdateCoursewareCommand command);

    Result<?> deleteCourseware(DeleteCoursewareCommand command);

    Result<?> reorderCoursewares(ReorderCoursewaresCommand command);

    Result<?> deleteCover(Long courseId);
}
