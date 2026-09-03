package me.lj.train.api.training;

import me.lj.train.api.training.ExamModels.ExamRecordView;
import me.lj.train.api.training.ExamModels.SaveAnswersCommand;
import me.lj.train.common.core.result.Result;

/**
 * 学员一次考试、答题保存和交卷RPC。
 */
public interface ExamService {

    Result<ExamRecordView> open(Long planId);

    Result<ExamRecordView> get(Long recordId);

    Result<ExamRecordView> saveAnswers(SaveAnswersCommand command);

    Result<ExamRecordView> submit(Long recordId);
}
