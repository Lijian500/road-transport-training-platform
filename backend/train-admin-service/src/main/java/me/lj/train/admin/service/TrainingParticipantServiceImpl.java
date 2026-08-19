package me.lj.train.admin.service;

import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import me.lj.train.admin.constant.AdminConstants;
import me.lj.train.admin.constant.AdminPermissions;
import me.lj.train.admin.mapper.OrgMapper;
import me.lj.train.admin.mapper.RoleMapper;
import me.lj.train.admin.mapper.UserMapper;
import me.lj.train.admin.mapper.UserRoleMapper;
import me.lj.train.admin.model.entity.OrgEntity;
import me.lj.train.admin.model.entity.RoleEntity;
import me.lj.train.admin.model.entity.UserEntity;
import me.lj.train.admin.support.AdminGuard;
import me.lj.train.api.admin.TrainingParticipantModels.ParticipantQuery;
import me.lj.train.api.admin.TrainingParticipantModels.ParticipantView;
import me.lj.train.api.admin.TrainingParticipantModels.ValidateParticipantsCommand;
import me.lj.train.api.admin.TrainingParticipantService;
import me.lj.train.common.core.exception.BusinessException;
import me.lj.train.common.core.result.AppErrorCode;
import me.lj.train.common.core.result.Result;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static me.lj.train.admin.model.table.OrgTableDef.ORG;
import static me.lj.train.admin.model.table.RoleTableDef.ROLE;
import static me.lj.train.admin.model.table.UserRoleTableDef.USER_ROLE;
import static me.lj.train.admin.model.table.UserTableDef.USER;

/**
 * 培训计划学员候选与发布校验RPC实现。
 */
@DubboService(timeout = 5000, retries = 0)
public class TrainingParticipantServiceImpl extends AdminServiceSupport
        implements TrainingParticipantService {

    private static final int MAX_CANDIDATES = 500;

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final OrgMapper orgMapper;

    public TrainingParticipantServiceImpl(
            PlatformTransactionManager transactionManager,
            UserMapper userMapper,
            RoleMapper roleMapper,
            UserRoleMapper userRoleMapper,
            OrgMapper orgMapper) {
        super(transactionManager);
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.orgMapper = orgMapper;
    }

    @Override
    public Result<List<ParticipantView>> listCandidates(ParticipantQuery query) {
        return execute(() -> {
            Long enterpriseId = requirePlanPermission();
            RoleEntity studentRole = findStudentRole(enterpriseId);
            if (studentRole == null) {
                return Collections.emptyList();
            }
            List<Long> userIds = listStudentUserIds(enterpriseId, studentRole.getId(), null);
            if (userIds.isEmpty()) {
                return Collections.emptyList();
            }
            String keyword = query == null ? null : trim(query.keyword());
            Long orgId = query == null ? null : query.orgId();
            QueryWrapper wrapper = QueryWrapper.create()
                    .where(USER.ENTERPRISE_ID.eq(enterpriseId))
                    .and(USER.ID.in(userIds))
                    .and(USER.STATUS.eq(AdminConstants.STATUS_ENABLED))
                    .and(USER.ORG_ID.eq(orgId).when(orgId != null))
                    .and(USER.USERNAME.like(keyword)
                            .or(USER.DISPLAY_NAME.like(keyword))
                            .when(hasText(keyword)))
                    .orderBy(USER.DISPLAY_NAME.asc(), USER.USERNAME.asc());
            Page<UserEntity> page = userMapper.paginate(1, MAX_CANDIDATES, wrapper);
            return toViews(page.getRecords());
        });
    }

    @Override
    public Result<List<ParticipantView>> validate(ValidateParticipantsCommand command) {
        return execute(() -> {
            Long enterpriseId = requirePlanPermission();
            List<Long> userIds = command == null || command.userIds() == null
                    ? Collections.emptyList()
                    : new LinkedHashSet<Long>(command.userIds()).stream()
                            .filter(id -> id != null)
                            .collect(Collectors.toList());
            if (userIds.isEmpty()) {
                return Collections.emptyList();
            }
            RoleEntity studentRole = findStudentRole(enterpriseId);
            if (studentRole == null) {
                throw new BusinessException(AppErrorCode.PLAN_PARTICIPANT_INVALID,
                        "当前组织没有可参训的学员角色");
            }
            Set<Long> roleUserIds = new LinkedHashSet<Long>(
                    listStudentUserIds(enterpriseId, studentRole.getId(), userIds));
            List<UserEntity> users = userMapper.selectListByQuery(QueryWrapper.create()
                    .where(USER.ENTERPRISE_ID.eq(enterpriseId))
                    .and(USER.ID.in(userIds))
                    .and(USER.STATUS.eq(AdminConstants.STATUS_ENABLED)));
            Map<Long, UserEntity> userMap = users.stream()
                    .filter(user -> roleUserIds.contains(user.getId()))
                    .collect(Collectors.toMap(UserEntity::getId, Function.identity()));
            if (userMap.size() != userIds.size()) {
                throw new BusinessException(AppErrorCode.PLAN_PARTICIPANT_INVALID,
                        "参训人员必须是当前组织内已启用的学员");
            }
            return toViews(userIds.stream().map(userMap::get).collect(Collectors.toList()));
        });
    }

    private Long requirePlanPermission() {
        return AdminGuard.requireEnterpriseAnyPermission(
                AdminPermissions.PLAN_VIEW,
                AdminPermissions.PLAN_CREATE,
                AdminPermissions.PLAN_UPDATE,
                AdminPermissions.PLAN_PUBLISH);
    }

    private RoleEntity findStudentRole(Long enterpriseId) {
        return roleMapper.selectOneByQuery(QueryWrapper.create()
                .where(ROLE.ENTERPRISE_ID.eq(enterpriseId))
                .and(ROLE.ROLE_CODE.eq(AdminConstants.ROLE_STUDENT))
                .and(ROLE.STATUS.eq(AdminConstants.STATUS_ENABLED))
                .and(ROLE.DELETED_AT.isNull()));
    }

    private List<Long> listStudentUserIds(
            Long enterpriseId, Long roleId, List<Long> requestedUserIds) {
        QueryWrapper wrapper = QueryWrapper.create()
                .where(USER_ROLE.ENTERPRISE_ID.eq(enterpriseId))
                .and(USER_ROLE.ROLE_ID.eq(roleId));
        if (requestedUserIds != null && !requestedUserIds.isEmpty()) {
            wrapper.and(USER_ROLE.USER_ID.in(requestedUserIds));
        }
        return userRoleMapper.selectListByQuery(wrapper)
                .stream()
                .map(relation -> relation.getUserId())
                .distinct()
                .collect(Collectors.toList());
    }

    private List<ParticipantView> toViews(List<UserEntity> users) {
        if (users == null || users.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> orgIds = users.stream()
                .map(UserEntity::getOrgId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        Map<Long, OrgEntity> orgMap = orgIds.isEmpty()
                ? Collections.emptyMap()
                : orgMapper.selectListByQuery(QueryWrapper.create()
                                .where(ORG.ID.in(orgIds))
                                .and(ORG.DELETED_AT.isNull()))
                        .stream()
                        .collect(Collectors.toMap(OrgEntity::getId, Function.identity()));
        return users.stream().map(user -> {
            OrgEntity org = orgMap.get(user.getOrgId());
            return new ParticipantView(
                    user.getId(), user.getEnterpriseId(), user.getOrgId(),
                    org == null ? null : org.getOrgName(), user.getUsername(), user.getDisplayName());
        }).collect(Collectors.toList());
    }

    private boolean hasText(String value) {
        return value != null && !value.isEmpty();
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }
}
