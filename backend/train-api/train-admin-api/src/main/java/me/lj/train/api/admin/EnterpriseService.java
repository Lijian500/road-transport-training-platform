package me.lj.train.api.admin;

import me.lj.train.api.admin.AdminModels.ChangeStatusCommand;
import me.lj.train.api.admin.AdminModels.CreateEnterpriseCommand;
import me.lj.train.api.admin.AdminModels.EnterpriseQuery;
import me.lj.train.api.admin.AdminModels.EnterpriseView;
import me.lj.train.api.admin.AdminModels.UpdateEnterpriseCommand;
import me.lj.train.common.core.page.PageResult;
import me.lj.train.common.core.result.Result;

/**
 * 平台企业管理RPC接口。
 */
public interface EnterpriseService {

    Result<PageResult<EnterpriseView>> page(EnterpriseQuery query);

    Result<EnterpriseView> create(CreateEnterpriseCommand command);

    Result<EnterpriseView> update(UpdateEnterpriseCommand command);

    Result<?> changeStatus(ChangeStatusCommand command);
}
