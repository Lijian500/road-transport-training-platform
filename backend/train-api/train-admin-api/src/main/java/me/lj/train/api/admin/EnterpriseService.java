package me.lj.train.api.admin;

import me.lj.train.api.admin.AdminModels.ChangeStatusCommand;
import me.lj.train.api.admin.AdminModels.CreateEnterpriseCommand;
import me.lj.train.api.admin.AdminModels.EnterpriseAdministratorView;
import me.lj.train.api.admin.AdminModels.EnterpriseQuery;
import me.lj.train.api.admin.AdminModels.EnterpriseView;
import me.lj.train.api.admin.AdminModels.ResetEnterpriseAdministratorPasswordCommand;
import me.lj.train.api.admin.AdminModels.UpdateEnterpriseCommand;
import me.lj.train.common.core.page.PageResult;
import me.lj.train.common.core.result.Result;

import java.util.List;

/**
 * 平台企业及行管组织管理RPC接口。
 */
public interface EnterpriseService {

    Result<PageResult<EnterpriseView>> page(EnterpriseQuery query);

    Result<EnterpriseView> create(CreateEnterpriseCommand command);

    Result<EnterpriseView> update(UpdateEnterpriseCommand command);

    Result<?> changeStatus(ChangeStatusCommand command);

    Result<List<EnterpriseAdministratorView>> listAdministrators(Long enterpriseId);

    Result<?> resetAdministratorPassword(ResetEnterpriseAdministratorPasswordCommand command);
}
