package me.lj.train.api.training;

import me.lj.train.api.training.ExamModels.CreatePaperCommand;
import me.lj.train.api.training.ExamModels.PaperOptionView;
import me.lj.train.api.training.ExamModels.PaperQuery;
import me.lj.train.api.training.ExamModels.PaperView;
import me.lj.train.api.training.ExamModels.UpdatePaperCommand;
import me.lj.train.common.core.page.PageResult;
import me.lj.train.common.core.result.Result;

import java.util.List;

/**
 * 试卷草稿、选题和启用固化RPC。
 */
public interface ExamPaperService {

    Result<PageResult<PaperView>> page(PaperQuery query);

    Result<PaperView> create(CreatePaperCommand command);

    Result<PaperView> get(Long id);

    Result<PaperView> update(UpdatePaperCommand command);

    Result<?> delete(Long id);

    Result<PaperView> enable(Long id);

    Result<List<PaperOptionView>> listEnabled(String keyword);
}
