package me.lj.train.api.training;

import me.lj.train.api.training.ExamModels.CreateQuestionCommand;
import me.lj.train.api.training.ExamModels.QuestionQuery;
import me.lj.train.api.training.ExamModels.QuestionView;
import me.lj.train.api.training.ExamModels.UpdateQuestionCommand;
import me.lj.train.common.core.page.PageResult;
import me.lj.train.common.core.result.Result;

/**
 * 单选题、判断题题库管理RPC。
 */
public interface ExamQuestionService {

    Result<PageResult<QuestionView>> page(QuestionQuery query);

    Result<QuestionView> create(CreateQuestionCommand command);

    Result<QuestionView> get(Long id);

    Result<QuestionView> update(UpdateQuestionCommand command);

    Result<QuestionView> changeStatus(Long id, String status);

    Result<?> delete(Long id);
}
