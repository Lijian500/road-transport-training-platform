package me.lj.train.admin.mapper;

import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.update.UpdateWrapper;
import me.lj.train.admin.model.entity.OrgEntity;
import me.lj.train.admin.model.entity.OrgUserEntity;
import me.lj.train.admin.model.entity.PermissionEntity;
import me.lj.train.admin.model.entity.RoleEntity;
import me.lj.train.admin.model.entity.RolePermissionEntity;
import me.lj.train.admin.model.entity.UserEntity;
import me.lj.train.admin.model.entity.UserRoleEntity;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;

import static me.lj.train.admin.model.table.OrgTableDef.ORG;
import static me.lj.train.admin.model.table.OrgUserTableDef.ORG_USER;
import static me.lj.train.admin.model.table.PermissionTableDef.PERMISSION;
import static me.lj.train.admin.model.table.RolePermissionTableDef.ROLE_PERMISSION;
import static me.lj.train.admin.model.table.UserRoleTableDef.USER_ROLE;
import static me.lj.train.admin.model.table.UserTableDef.USER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 管理服务 Mapper 的 MySQL 集成测试。
 *
 * <p>仅装配数据源、Flyway 与 MyBatis-Flex，避免连接 Dubbo、Nacos 和 Redis。</p>
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
        classes = AdminMapperIntegrationTest.MapperTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.config.name=mapper-integration-test",
                "spring.application.name=admin-mapper-integration-test",
                "spring.flyway.enabled=true",
                "spring.flyway.locations=classpath:db/migration",
                "mybatis-flex.mapper-locations=classpath*:/mappers/**/*.xml",
                "mybatis-flex.configuration.map-underscore-to-camel-case=true",
                "spring.cloud.nacos.config.enabled=false",
                "spring.cloud.nacos.discovery.enabled=false",
                "dubbo.enabled=false",
                "app.bootstrap.enabled=false"
        })
class AdminMapperIntegrationTest {

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("road_training_admin_test")
            .withUsername("train_admin_test")
            .withPassword("train_admin_test")
            .withUrlParam("serverTimezone", "Asia/Shanghai")
            .withUrlParam("useUnicode", "true")
            .withUrlParam("characterEncoding", "UTF-8");

    @Autowired
    private OrgMapper orgMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private RoleMapper roleMapper;
    @Autowired
    private OrgUserMapper orgUserMapper;
    @Autowired
    private UserRoleMapper userRoleMapper;
    @Autowired
    private RolePermissionMapper rolePermissionMapper;
    @Autowired
    private PermissionMapper permissionMapper;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
    }

    @Test
    void shouldQueryAndPaginateOnlyWithinSpecifiedEnterprise() {
        OrgEntity enterpriseOne = enterprise(110_000L, "ENT-110", "企业一");
        OrgEntity departmentOne = department(110_001L, enterpriseOne.getId(), "DEPT-110-1", "一号部门", 1);
        OrgEntity departmentTwo = department(110_002L, enterpriseOne.getId(), "DEPT-110-2", "二号部门", 2);
        OrgEntity enterpriseTwo = enterprise(120_000L, "ENT-120", "企业二");
        OrgEntity otherDepartment = department(120_001L, enterpriseTwo.getId(), "DEPT-120-1", "其他部门", 1);

        insertOrganizations(enterpriseOne, departmentOne, departmentTwo, enterpriseTwo, otherDepartment);

        QueryWrapper enterpriseWrapper = QueryWrapper.create()
                .where(ORG.ENTERPRISE_ID.eq(enterpriseOne.getId()))
                .and(ORG.ORG_TYPE.eq("DEPARTMENT"))
                .orderBy(ORG.SORT_ORDER.asc());
        List<OrgEntity> departments = orgMapper.selectListByQuery(enterpriseWrapper);
        Page<OrgEntity> page = orgMapper.paginate(1, 1, enterpriseWrapper);

        assertThat(departments)
                .extracting(OrgEntity::getId)
                .containsExactly(departmentOne.getId(), departmentTwo.getId());
        assertThat(page.getTotalRow()).isEqualTo(2);
        assertThat(page.getRecords())
                .extracting(OrgEntity::getId)
                .containsExactly(departmentOne.getId());
        assertThat(departments)
                .extracting(OrgEntity::getEnterpriseId)
                .containsOnly(enterpriseOne.getId());
    }

    @Test
    void shouldLoadComplexQueriesFromMapperXml() {
        long enterpriseOne = 210_000L;
        long enterpriseTwo = 220_000L;
        RoleEntity enterpriseAdminOne = role(
                211_000L, enterpriseOne, "ENTERPRISE_ADMIN", "企业一管理员", true);
        RoleEntity customRole = role(
                211_001L, enterpriseOne, "USER_OPERATOR", "用户操作员", false);
        RoleEntity deletedRole = role(
                211_002L, enterpriseOne, "DELETED_ROLE", "已删除角色", false);
        deletedRole.setDeletedBy(1L);
        deletedRole.setDeletedAt(LocalDateTime.now());
        RoleEntity enterpriseAdminTwo = role(
                221_000L, enterpriseTwo, "ENTERPRISE_ADMIN", "企业二管理员", true);
        UserEntity enabledAdminOne = user(212_000L, enterpriseOne, "admin-one", "ENABLED", 1);
        UserEntity disabledAdminOne = user(212_001L, enterpriseOne, "admin-one-disabled", "DISABLED", 1);
        UserEntity enabledAdminTwo = user(222_000L, enterpriseTwo, "admin-two", "ENABLED", 1);

        roleMapper.insertSelective(enterpriseAdminOne);
        roleMapper.insertSelective(customRole);
        roleMapper.insertSelective(deletedRole);
        roleMapper.insertSelective(enterpriseAdminTwo);
        userMapper.insertSelective(enabledAdminOne);
        userMapper.insertSelective(disabledAdminOne);
        userMapper.insertSelective(enabledAdminTwo);
        insertUserRole(enabledAdminOne.getId(), enterpriseAdminOne.getId(), enterpriseOne);
        insertUserRole(enabledAdminOne.getId(), customRole.getId(), enterpriseOne);
        insertUserRole(enabledAdminOne.getId(), deletedRole.getId(), enterpriseOne);
        insertUserRole(disabledAdminOne.getId(), enterpriseAdminOne.getId(), enterpriseOne);
        insertUserRole(enabledAdminTwo.getId(), enterpriseAdminTwo.getId(), enterpriseTwo);
        insertRolePermission(enterpriseAdminOne.getId(), 400L);
        insertRolePermission(customRole.getId(), 401L);
        insertRolePermission(deletedRole.getId(), 402L);

        assertThat(roleMapper.listByUserId(enabledAdminOne.getId()))
                .extracting(RoleEntity::getRoleCode)
                .containsExactly("ENTERPRISE_ADMIN", "USER_OPERATOR");
        assertThat(roleMapper.listPermissionCodesByUserId(enabledAdminOne.getId()))
                .containsExactly("admin:user:view", "admin:user:create");
        assertThat(roleMapper.listPermissionCodesByRoleId(customRole.getId()))
                .containsExactly("admin:user:create");
        assertThat(roleMapper.listPermissionCodesByRoleId(deletedRole.getId())).isEmpty();
        assertThat(roleMapper.listPermissionCodesByRoleIds(
                List.of(enterpriseAdminOne.getId(), customRole.getId(), deletedRole.getId())))
                .containsExactlyInAnyOrder("admin:user:view", "admin:user:create");
        assertThat(userMapper.countEnabledEnterpriseAdmins(enterpriseOne)).isEqualTo(1);
        assertThat(userMapper.countEnabledEnterpriseAdmins(enterpriseTwo)).isEqualTo(1);
    }

    @Test
    void shouldInsertAndDeleteAllRelationEntities() {
        long enterpriseId = 310_000L;
        long departmentId = 310_001L;
        long userId = 312_000L;
        long roleId = 311_000L;
        OrgEntity enterprise = enterprise(enterpriseId, "ENT-310", "关联测试企业");
        OrgEntity department = department(
                departmentId, enterpriseId, "DEPT-310-1", "关联测试部门", 1);
        UserEntity user = user(userId, enterpriseId, "relation-user", "ENABLED", 1);
        user.setOrgId(departmentId);
        RoleEntity role = role(roleId, enterpriseId, "RELATION_ROLE", "关联测试角色", false);

        insertOrganizations(enterprise, department);
        userMapper.insertSelective(user);
        roleMapper.insertSelective(role);
        insertOrgUser(userId, departmentId, enterpriseId);
        insertUserRole(userId, roleId, enterpriseId);
        insertRolePermission(roleId, 400L);

        assertThat(orgUserMapper.selectCountByQuery(
                QueryWrapper.create().where(ORG_USER.USER_ID.eq(userId)))).isEqualTo(1);
        assertThat(userRoleMapper.selectCountByQuery(
                QueryWrapper.create().where(USER_ROLE.USER_ID.eq(userId)))).isEqualTo(1);
        assertThat(rolePermissionMapper.selectCountByQuery(
                QueryWrapper.create().where(ROLE_PERMISSION.ROLE_ID.eq(roleId)))).isEqualTo(1);

        assertThat(orgUserMapper.deleteByQuery(
                QueryWrapper.create().where(ORG_USER.USER_ID.eq(userId)))).isEqualTo(1);
        assertThat(userRoleMapper.deleteByQuery(
                QueryWrapper.create().where(USER_ROLE.USER_ID.eq(userId)))).isEqualTo(1);
        assertThat(rolePermissionMapper.deleteByQuery(
                QueryWrapper.create().where(ROLE_PERMISSION.ROLE_ID.eq(roleId)))).isEqualTo(1);

        UserRoleEntity replacement = new UserRoleEntity();
        replacement.setUserId(userId);
        replacement.setRoleId(roleId + 1);
        replacement.setEnterpriseId(enterpriseId);
        assertThat(userRoleMapper.insertBatch(List.of(replacement))).isEqualTo(1);
        assertThat(userRoleMapper.selectListByQuery(
                        QueryWrapper.create().where(USER_ROLE.USER_ID.eq(userId))))
                .extracting(UserRoleEntity::getRoleId)
                .containsExactly(roleId + 1);
    }

    @Test
    void shouldIncrementLoginVersionWithDatabaseExpression() {
        UserEntity user = user(412_000L, 410_000L, "version-user", "ENABLED", 7);
        userMapper.insertSelective(user);

        UpdateWrapper<UserEntity> update = UpdateWrapper.of(UserEntity.class)
                .set(USER.LOGIN_VERSION, USER.LOGIN_VERSION.add(1));
        int updatedRows = userMapper.updateByCondition(
                update.toEntity(), USER.ID.eq(user.getId()));

        assertThat(updatedRows).isEqualTo(1);
        assertThat(userMapper.selectOneById(user.getId()).getLoginVersion()).isEqualTo(8);
    }

    @Test
    void shouldMigrateCompleteCoursePermissionCatalog() {
        List<PermissionEntity> permissions = permissionMapper.selectListByQuery(QueryWrapper.create()
                .where(PERMISSION.PERMISSION_CODE.likeRight("admin:course"))
                .orderBy(PERMISSION.SORT_ORDER.asc()));

        assertThat(permissions)
                .extracting(PermissionEntity::getPermissionCode)
                .containsExactly(
                        "admin:course:view",
                        "admin:course:create",
                        "admin:course:update",
                        "admin:course:status",
                        "admin:course:delete",
                        "admin:courseware:manage");
        assertThat(permissions).allSatisfy(permission ->
                assertThat(permission.getPermissionScope()).isEqualTo("ENTERPRISE"));
    }

    @Test
    void shouldRollbackChangesAcrossMultipleMappers() {
        long enterpriseId = 510_000L;
        long userId = 512_000L;
        OrgEntity enterprise = enterprise(enterpriseId, "ENT-510", "事务回滚企业");
        UserEntity user = user(userId, enterpriseId, "rollback-user", "ENABLED", 1);
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> transactionTemplate.execute(status -> {
            orgMapper.insertSelective(enterprise);
            userMapper.insertSelective(user);
            throw new IllegalStateException("模拟多表业务失败");
        })).isInstanceOf(IllegalStateException.class)
                .hasMessage("模拟多表业务失败");

        assertThat(orgMapper.selectOneById(enterpriseId)).isNull();
        assertThat(userMapper.selectOneById(userId)).isNull();
    }

    private void insertOrganizations(OrgEntity... organizations) {
        for (OrgEntity organization : organizations) {
            assertThat(orgMapper.insertSelective(organization)).isEqualTo(1);
        }
    }

    private void insertOrgUser(long userId, long orgId, long enterpriseId) {
        OrgUserEntity relation = new OrgUserEntity();
        relation.setUserId(userId);
        relation.setOrgId(orgId);
        relation.setEnterpriseId(enterpriseId);
        relation.setPrimary(true);
        assertThat(orgUserMapper.insertSelective(relation)).isEqualTo(1);
    }

    private void insertUserRole(long userId, long roleId, long enterpriseId) {
        UserRoleEntity relation = new UserRoleEntity();
        relation.setUserId(userId);
        relation.setRoleId(roleId);
        relation.setEnterpriseId(enterpriseId);
        assertThat(userRoleMapper.insertSelective(relation)).isEqualTo(1);
    }

    private void insertRolePermission(long roleId, long permissionId) {
        RolePermissionEntity relation = new RolePermissionEntity();
        relation.setRoleId(roleId);
        relation.setPermissionId(permissionId);
        assertThat(rolePermissionMapper.insertSelective(relation)).isEqualTo(1);
    }

    private static OrgEntity enterprise(long id, String code, String name) {
        OrgEntity entity = new OrgEntity();
        entity.setId(id);
        entity.setEnterpriseId(id);
        entity.setOrgType("ENTERPRISE");
        entity.setOrgCode(code);
        entity.setOrgName(name);
        entity.setStatus("ENABLED");
        return entity;
    }

    private static OrgEntity department(
            long id, long enterpriseId, String code, String name, int sortOrder) {
        OrgEntity entity = new OrgEntity();
        entity.setId(id);
        entity.setEnterpriseId(enterpriseId);
        entity.setParentId(enterpriseId);
        entity.setOrgType("DEPARTMENT");
        entity.setOrgCode(code);
        entity.setOrgName(name);
        entity.setStatus("ENABLED");
        entity.setSortOrder(sortOrder);
        return entity;
    }

    private static RoleEntity role(
            long id, long enterpriseId, String code, String name, boolean builtIn) {
        RoleEntity entity = new RoleEntity();
        entity.setId(id);
        entity.setEnterpriseId(enterpriseId);
        entity.setRoleCode(code);
        entity.setRoleName(name);
        entity.setStatus("ENABLED");
        entity.setBuiltIn(builtIn);
        return entity;
    }

    private static UserEntity user(
            long id, long enterpriseId, String username, String status, long loginVersion) {
        UserEntity entity = new UserEntity();
        entity.setId(id);
        entity.setEnterpriseId(enterpriseId);
        entity.setUsername(username);
        entity.setPasswordHash("$2a$10$integration.test.password.hash");
        entity.setDisplayName(username);
        entity.setStatus(status);
        entity.setLoginVersion(loginVersion);
        entity.setMustChangePassword(true);
        entity.setPlatformAdmin(false);
        return entity;
    }

    /**
     * Mapper 测试最小上下文，只导入数据库相关自动配置。
     */
    @SpringBootConfiguration(proxyBeanMethods = false)
    @ImportAutoConfiguration({
            DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class,
            FlywayAutoConfiguration.class,
            com.mybatisflex.spring.boot.MybatisFlexAutoConfiguration.class
    })
    @MapperScan("me.lj.train.admin.mapper")
    static class MapperTestApplication {
    }
}
