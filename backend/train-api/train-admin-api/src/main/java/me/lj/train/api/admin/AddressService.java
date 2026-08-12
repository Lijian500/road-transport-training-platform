package me.lj.train.api.admin;

import me.lj.train.api.admin.AddressModels.AddressView;
import me.lj.train.api.admin.AddressModels.CreateAddressCommand;
import me.lj.train.api.admin.AddressModels.UpdateAddressCommand;
import me.lj.train.common.core.result.Result;

import java.util.List;

/**
 * 省、市、区三级行政地址管理RPC接口。
 */
public interface AddressService {

    /**
     * 按父级行政代码查询直属下级，根节点父级代码为0。
     */
    Result<List<AddressView>> children(String parentCode);

    /**
     * 新增省级地址或指定地址的下级。
     */
    Result<AddressView> create(CreateAddressCommand command);

    /**
     * 编辑地址基本信息。
     */
    Result<AddressView> update(UpdateAddressCommand command);
}
