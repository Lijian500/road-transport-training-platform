package me.lj.train.training.mapper;

import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import me.lj.train.training.model.entity.PlanEntity;

/** 培训计划Mapper。 */
public interface PlanMapper extends BaseMapper<PlanEntity> {

    /** 全部有效学员结业后结束计划；限定企业，并排除空计划和取消任务。 */
    @Update({"<script>",
            "UPDATE train_plan p SET status = 'FINISHED', updated_by = 0",
            "WHERE p.status IN ('PUBLISHED', 'IN_PROGRESS') AND p.deleted_at IS NULL",
            "<if test='enterpriseId != null'>AND p.enterprise_id = #{enterpriseId}</if>",
            "<if test='planId != null'>AND p.id = #{planId}</if>",
            "AND EXISTS (SELECT 1 FROM train_plan_user u WHERE u.plan_id = p.id",
            "AND u.enterprise_id = p.enterprise_id AND u.assignment_status = 'ASSIGNED')",
            "AND NOT EXISTS (SELECT 1 FROM train_plan_user u WHERE u.plan_id = p.id",
            "AND u.enterprise_id = p.enterprise_id AND u.assignment_status = 'ASSIGNED'",
            "AND (u.completion_status IS NULL OR u.completion_status != 'COMPLETED'))",
            "</script>"})
    int finishCompletedPlans(@Param("enterpriseId") Long enterpriseId, @Param("planId") Long planId);

}
