package me.lj.train.admin.service;

import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.update.UpdateWrapper;
import me.lj.train.admin.constant.AdminConstants;
import me.lj.train.admin.constant.AdminPermissions;
import me.lj.train.admin.mapper.OrgMapper;
import me.lj.train.admin.mapper.UserMapper;
import me.lj.train.admin.model.entity.OrgEntity;
import me.lj.train.admin.support.AdminGuard;
import me.lj.train.api.admin.AdminModels.CreateOrgCommand;
import me.lj.train.api.admin.AdminModels.OrgView;
import me.lj.train.api.admin.AdminModels.UpdateOrgCommand;
import me.lj.train.api.admin.OrgService;
import me.lj.train.common.core.exception.BusinessException;
import me.lj.train.common.core.result.AppErrorCode;
import me.lj.train.common.core.result.Result;
import me.lj.train.common.core.util.IdGenerator;
import me.lj.train.common.security.context.UserContext;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static me.lj.train.admin.model.table.OrgTableDef.ORG;
import static me.lj.train.admin.model.table.UserTableDef.USER;

/**
 * 组织树RPC实现，直接编排MyBatis-Flex Mapper。
 */
@DubboService(timeout = 5000, retries = 0)
public class OrgServiceImpl extends AdminServiceSupport implements OrgService {

    private final OrgMapper orgMapper;
    private final UserMapper userMapper;

    public OrgServiceImpl(
            PlatformTransactionManager transactionManager,
            OrgMapper orgMapper,
            UserMapper userMapper) {
        super(transactionManager);
        this.orgMapper = orgMapper;
        this.userMapper = userMapper;
    }

    @Override
    public Result<List<OrgView>> tree() {
        return execute(() -> {
            Long enterpriseId = AdminGuard.requireEnterprisePermission(AdminPermissions.ORG_VIEW);
            List<OrgEntity> entities = listByEnterprise(enterpriseId);
            Map<Long, List<OrgEntity>> childrenMap = entities.stream()
                    .filter(entity -> entity.getParentId() != null)
                    .collect(Collectors.groupingBy(OrgEntity::getParentId));
            return entities.stream()
                    .filter(entity -> entity.getParentId() == null)
                    .map(entity -> toTree(entity, childrenMap))
                    .collect(Collectors.toList());
        });
    }

    @Override
    public Result<OrgView> create(CreateOrgCommand command) {
        return executeTransactional(() -> {
            Long enterpriseId = AdminGuard.requireEnterprisePermission(AdminPermissions.ORG_CREATE);
            Long parentId = command.parentId() == null ? enterpriseId : command.parentId();
            OrgEntity parent = requireOrg(parentId, enterpriseId);
            String name = AdminGuard.requireText(command.name(), "部门名称");
            String code = AdminGuard.normalizeCode(command.code(), "部门编码");
            if (countSiblingName(enterpriseId, parentId, name, null) > 0) {
                throw new BusinessException(AppErrorCode.ORG_NAME_EXISTS);
            }
            if (findByCode(enterpriseId, code) != null) {
                throw new BusinessException(AppErrorCode.ORG_CODE_EXISTS);
            }

            OrgEntity entity = new OrgEntity();
            entity.setId(IdGenerator.nextId());
            entity.setEnterpriseId(enterpriseId);
            entity.setParentId(parent.getId());
            entity.setOrgType(AdminConstants.ORG_DEPARTMENT);
            entity.setOrgCode(code);
            entity.setOrgName(name);
            entity.setStatus(AdminConstants.STATUS_ENABLED);
            entity.setSortOrder(command.sortOrder());
            entity.setCreatedBy(UserContext.require().getUserId());
            entity.setUpdatedBy(UserContext.require().getUserId());
            orgMapper.insertSelective(entity);
            return toView(orgMapper.selectOneById(entity.getId()), new ArrayList<OrgView>());
        });
    }

    @Override
    public Result<OrgView> update(UpdateOrgCommand command) {
        return executeTransactional(() -> {
            Long enterpriseId = AdminGuard.requireEnterprisePermission(AdminPermissions.ORG_UPDATE);
            OrgEntity entity = requireOrg(command.id(), enterpriseId);
            if (!AdminConstants.ORG_DEPARTMENT.equals(entity.getOrgType())) {
                throw new BusinessException(AppErrorCode.BUILTIN_DATA_READONLY, "企业根节点不能在部门管理中修改");
            }
            Long parentId = command.parentId() == null ? enterpriseId : command.parentId();
            OrgEntity parent = requireOrg(parentId, enterpriseId);
            if (entity.getId().equals(parent.getId())
                    || isDescendant(entity.getId(), parent.getId(), enterpriseId)) {
                throw new BusinessException(AppErrorCode.PARAM_INVALID, "不能将部门移动到自身或其下级部门");
            }
            String name = AdminGuard.requireText(command.name(), "部门名称");
            String code = AdminGuard.normalizeCode(command.code(), "部门编码");
            if (countSiblingName(enterpriseId, parentId, name, entity.getId()) > 0) {
                throw new BusinessException(AppErrorCode.ORG_NAME_EXISTS);
            }
            OrgEntity sameCode = findByCode(enterpriseId, code);
            if (sameCode != null && !sameCode.getId().equals(entity.getId())) {
                throw new BusinessException(AppErrorCode.ORG_CODE_EXISTS);
            }
            UpdateWrapper<OrgEntity> update = UpdateWrapper.of(OrgEntity.class)
                    .set(ORG.PARENT_ID, parentId)
                    .set(ORG.ORG_NAME, name)
                    .set(ORG.ORG_CODE, code)
                    .set(ORG.SORT_ORDER, command.sortOrder())
                    .set(ORG.UPDATED_BY, UserContext.require().getUserId());
            orgMapper.updateByCondition(
                    update.toEntity(),
                    ORG.ID.eq(entity.getId()).and(ORG.ORG_TYPE.eq(AdminConstants.ORG_DEPARTMENT)));
            return toView(orgMapper.selectOneById(entity.getId()), new ArrayList<OrgView>());
        });
    }

    @Override
    public Result<?> delete(Long id) {
        return executeVoidTransactional(() -> {
            Long enterpriseId = AdminGuard.requireEnterprisePermission(AdminPermissions.ORG_DELETE);
            OrgEntity entity = requireOrg(id, enterpriseId);
            if (!AdminConstants.ORG_DEPARTMENT.equals(entity.getOrgType())) {
                throw new BusinessException(AppErrorCode.BUILTIN_DATA_READONLY, "企业根节点不能删除");
            }
            if (countChildren(id) > 0 || countUsers(enterpriseId, id) > 0) {
                throw new BusinessException(AppErrorCode.DATA_IN_USE);
            }
            orgMapper.deleteById(id);
        });
    }

    private List<OrgEntity> listByEnterprise(Long enterpriseId) {
        return orgMapper.selectListByQuery(QueryWrapper.create()
                .where(ORG.ENTERPRISE_ID.eq(enterpriseId))
                .orderBy(ORG.SORT_ORDER.asc(), ORG.CREATED_AT.asc()));
    }

    private OrgEntity findByCode(Long enterpriseId, String code) {
        return orgMapper.selectOneByQuery(QueryWrapper.create()
                .where(ORG.ENTERPRISE_ID.eq(enterpriseId))
                .and(ORG.ORG_CODE.eq(code)));
    }

    private long countSiblingName(Long enterpriseId, Long parentId, String name, Long excludeId) {
        return orgMapper.selectCountByQuery(QueryWrapper.create()
                .where(ORG.ENTERPRISE_ID.eq(enterpriseId))
                .and(ORG.PARENT_ID.eq(parentId))
                .and(ORG.ORG_NAME.eq(name))
                .and(ORG.ID.ne(excludeId).when(excludeId != null)));
    }

    private long countChildren(Long parentId) {
        return orgMapper.selectCountByQuery(QueryWrapper.create().where(ORG.PARENT_ID.eq(parentId)));
    }

    private long countUsers(Long enterpriseId, Long orgId) {
        return userMapper.selectCountByQuery(QueryWrapper.create()
                .where(USER.ENTERPRISE_ID.eq(enterpriseId))
                .and(USER.ORG_ID.eq(orgId)));
    }

    private boolean isDescendant(Long sourceId, Long targetParentId, Long enterpriseId) {
        Map<Long, Long> parentMap = new HashMap<Long, Long>();
        for (OrgEntity entity : listByEnterprise(enterpriseId)) {
            parentMap.put(entity.getId(), entity.getParentId());
        }
        Long current = targetParentId;
        while (current != null) {
            if (sourceId.equals(current)) {
                return true;
            }
            current = parentMap.get(current);
        }
        return false;
    }

    private OrgEntity requireOrg(Long id, Long enterpriseId) {
        OrgEntity entity = id == null ? null : orgMapper.selectOneById(id);
        if (entity == null) {
            throw new BusinessException(AppErrorCode.ORG_NOT_FOUND);
        }
        AdminGuard.checkEnterprise(entity.getEnterpriseId(), enterpriseId);
        return entity;
    }

    private OrgView toTree(OrgEntity entity, Map<Long, List<OrgEntity>> childrenMap) {
        List<OrgView> children = childrenMap.getOrDefault(entity.getId(), new ArrayList<OrgEntity>())
                .stream()
                .map(child -> toTree(child, childrenMap))
                .collect(Collectors.toList());
        return toView(entity, children);
    }

    private OrgView toView(OrgEntity entity, List<OrgView> children) {
        return new OrgView(
                entity.getId(),
                entity.getParentId(),
                entity.getOrgName(),
                entity.getOrgCode(),
                entity.getOrgType(),
                entity.getStatus(),
                entity.getSortOrder(),
                children);
    }
}
