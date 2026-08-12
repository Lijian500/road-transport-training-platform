package me.lj.train.admin.service;

import com.mybatisflex.core.query.QueryWrapper;
import me.lj.train.admin.constant.AdminPermissions;
import me.lj.train.admin.mapper.AddressMapper;
import me.lj.train.admin.model.entity.AddressEntity;
import me.lj.train.admin.support.AdminGuard;
import me.lj.train.api.admin.AddressModels.AddressView;
import me.lj.train.api.admin.AddressModels.CreateAddressCommand;
import me.lj.train.api.admin.AddressModels.UpdateAddressCommand;
import me.lj.train.api.admin.AddressService;
import me.lj.train.common.core.exception.BusinessException;
import me.lj.train.common.core.result.AppErrorCode;
import me.lj.train.common.core.result.Result;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static me.lj.train.admin.model.table.AddressTableDef.ADDRESS;

/**
 * 省、市、区三级行政地址RPC实现。
 */
@DubboService(timeout = 5000, retries = 0)
public class AddressServiceImpl extends AdminServiceSupport implements AddressService {

    private static final String ROOT_PARENT_CODE = "0";
    private static final int MAX_LEVEL = 3;
    private static final BigDecimal ZERO = new BigDecimal("0.000000");

    private final AddressMapper addressMapper;

    public AddressServiceImpl(
            PlatformTransactionManager transactionManager,
            AddressMapper addressMapper) {
        super(transactionManager);
        this.addressMapper = addressMapper;
    }

    /**
     * 按父级行政代码查询直属下级，避免一次加载全部地址。
     */
    @Override
    public Result<List<AddressView>> children(String parentCode) {
        return execute(() -> {
            AdminGuard.requirePlatformPermission(AdminPermissions.ADDRESS_VIEW);
            String normalizedParentCode = normalizeParentCode(parentCode);
            List<AddressEntity> entities = addressMapper.selectListByQuery(QueryWrapper.create()
                            .where(ADDRESS.PARENT_CODE.eq(normalizedParentCode))
                            .and(ADDRESS.LEVEL.between(1, MAX_LEVEL))
                            .orderBy(ADDRESS.AREA_CODE.asc()));
            Set<String> childParentCodes = findChildParentCodes(entities);
            return entities.stream()
                    .map(entity -> toView(entity, childParentCodes.contains(entity.getAreaCode())))
                    .collect(Collectors.toList());
        });
    }

    /**
     * 根据上级地址自动确定新增地址的层级。
     */
    @Override
    public Result<AddressView> create(CreateAddressCommand command) {
        return executeTransactional(() -> {
            AdminGuard.requirePlatformPermission(AdminPermissions.ADDRESS_CREATE);
            String parentCode = normalizeParentCode(command.getParentCode());
            AddressEntity parent = ROOT_PARENT_CODE.equals(parentCode) ? null : findByAreaCode(parentCode);
            if (!ROOT_PARENT_CODE.equals(parentCode) && parent == null) {
                throw invalid("上级地址不存在");
            }
            if (parent != null && (parent.getLevel() < 1 || parent.getLevel() >= MAX_LEVEL)) {
                throw invalid("上级地址层级不正确");
            }
            int level = parent == null ? 1 : parent.getLevel() + 1;
            if (level > MAX_LEVEL) {
                throw invalid("区县级地址不能继续新增下级");
            }
            AddressEntity entity = new AddressEntity();
            entity.setLevel(level);
            entity.setParentCode(parentCode);
            fill(entity, command.getAreaCode(), command.getZipCode(), command.getCityCode(),
                    command.getName(), command.getShortName(), command.getMergerName(),
                    command.getPinyin(), command.getLng(), command.getLat(), null);
            addressMapper.insertSelective(entity);
            return toView(entity, false);
        });
    }

    /**
     * 编辑地址资料；行政代码变化时同步直属下级的父级代码。
     */
    @Override
    public Result<AddressView> update(UpdateAddressCommand command) {
        return executeTransactional(() -> {
            AdminGuard.requirePlatformPermission(AdminPermissions.ADDRESS_UPDATE);
            AddressEntity entity = requireAddress(command.getId());
            String originalAreaCode = entity.getAreaCode();
            fill(entity, command.getAreaCode(), command.getZipCode(), command.getCityCode(),
                    command.getName(), command.getShortName(), command.getMergerName(),
                    command.getPinyin(), command.getLng(), command.getLat(), entity.getId());
            addressMapper.update(entity);
            if (!originalAreaCode.equals(entity.getAreaCode())) {
                AddressEntity childUpdate = new AddressEntity();
                childUpdate.setParentCode(entity.getAreaCode());
                addressMapper.updateByCondition(childUpdate, ADDRESS.PARENT_CODE.eq(originalAreaCode));
            }
            return toView(entity, countChildren(entity.getAreaCode()) > 0);
        });
    }

    /**
     * 校验并填充地址的可编辑字段。
     */
    private void fill(
            AddressEntity entity,
            String areaCode,
            String zipCode,
            String cityCode,
            String name,
            String shortName,
            String mergerName,
            String pinyin,
            BigDecimal lng,
            BigDecimal lat,
            Long excludeId) {
        String normalizedAreaCode = required(areaCode, "行政代码", 64);
        AddressEntity sameCode = findByAreaCode(normalizedAreaCode);
        if (sameCode != null && !sameCode.getId().equals(excludeId)) {
            throw invalid("行政代码已存在");
        }
        entity.setAreaCode(normalizedAreaCode);
        entity.setZipCode(optional(zipCode, "邮政编码", 64, ROOT_PARENT_CODE));
        entity.setCityCode(optional(cityCode, "区号", 64, ""));
        entity.setName(required(name, "名称", 50));
        entity.setShortName(optional(shortName, "简称", 50, ""));
        entity.setMergerName(optional(mergerName, "组合名", 50, entity.getName()));
        entity.setPinyin(optional(pinyin, "拼音", 30, ""));
        entity.setLng(lng == null ? ZERO : lng);
        entity.setLat(lat == null ? ZERO : lat);
    }

    /**
     * 按行政代码查询地址。
     */
    private AddressEntity findByAreaCode(String areaCode) {
        return addressMapper.selectOneByQuery(QueryWrapper.create()
                .where(ADDRESS.AREA_CODE.eq(areaCode)));
    }

    /**
     * 查询必须存在的地址。
     */
    private AddressEntity requireAddress(Long id) {
        AddressEntity entity = id == null ? null : addressMapper.selectOneById(id);
        if (entity == null) {
            throw invalid("地址不存在");
        }
        return entity;
    }

    /**
     * 批量查询本次返回节点中实际存在下级的行政代码。
     */
    private Set<String> findChildParentCodes(List<AddressEntity> entities) {
        List<String> areaCodes = entities.stream()
                .filter(entity -> entity.getLevel() < MAX_LEVEL)
                .map(AddressEntity::getAreaCode)
                .collect(Collectors.toList());
        if (areaCodes.isEmpty()) {
            return Collections.emptySet();
        }
        return addressMapper.selectListByQuery(QueryWrapper.create()
                        .where(ADDRESS.PARENT_CODE.in(areaCodes)))
                .stream()
                .map(AddressEntity::getParentCode)
                .collect(Collectors.toCollection(HashSet::new));
    }

    /**
     * 查询指定地址的直属下级数量。
     */
    private long countChildren(String parentCode) {
        return addressMapper.selectCountByQuery(QueryWrapper.create()
                .where(ADDRESS.PARENT_CODE.eq(parentCode)));
    }

    /**
     * 标准化根节点父级代码。
     */
    private String normalizeParentCode(String parentCode) {
        return parentCode == null || parentCode.trim().isEmpty()
                ? ROOT_PARENT_CODE
                : parentCode.trim();
    }

    /**
     * 校验必填文本和数据库字段长度。
     */
    private String required(String value, String fieldName, int maxLength) {
        String text = AdminGuard.requireText(value, fieldName);
        if (text.length() > maxLength) {
            throw invalid(fieldName + "不能超过" + maxLength + "个字符");
        }
        return text;
    }

    /**
     * 标准化非必填文本和数据库字段长度。
     */
    private String optional(String value, String fieldName, int maxLength, String defaultValue) {
        String text = value == null || value.trim().isEmpty() ? defaultValue : value.trim();
        if (text.length() > maxLength) {
            throw invalid(fieldName + "不能超过" + maxLength + "个字符");
        }
        return text;
    }

    /**
     * 转换地址响应模型。
     */
    private AddressView toView(AddressEntity entity, boolean hasChildren) {
        return new AddressView(
                entity.getId(), entity.getLevel(), entity.getParentCode(), entity.getAreaCode(),
                entity.getZipCode(), entity.getCityCode(), entity.getName(), entity.getShortName(),
                entity.getMergerName(), entity.getPinyin(), entity.getLng(), entity.getLat(),
                hasChildren);
    }

    /**
     * 创建参数错误异常。
     */
    private BusinessException invalid(String message) {
        return new BusinessException(AppErrorCode.PARAM_INVALID, message);
    }
}
