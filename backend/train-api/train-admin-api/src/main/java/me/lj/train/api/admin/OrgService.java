package me.lj.train.api.admin;

import me.lj.train.api.admin.AdminModels.CreateOrgCommand;
import me.lj.train.api.admin.AdminModels.OrgView;
import me.lj.train.api.admin.AdminModels.UpdateOrgCommand;
import me.lj.train.common.core.result.Result;

import java.util.List;

/**
 * 根组织部门树管理RPC接口。
 */
public interface OrgService {

    Result<List<OrgView>> tree();

    Result<OrgView> create(CreateOrgCommand command);

    Result<OrgView> update(UpdateOrgCommand command);

    Result<?> delete(Long id);
}
