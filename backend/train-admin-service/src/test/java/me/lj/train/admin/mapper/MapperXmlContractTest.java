package me.lj.train.admin.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 无需数据库即可校验复杂SQL XML可解析且声明的方法与Mapper契约一致。
 */
class MapperXmlContractTest {

    @Test
    void shouldLoadRoleAndUserMapperStatements() throws IOException {
        Configuration configuration = new Configuration();

        parse(configuration, "mappers/RoleMapper.xml");
        parse(configuration, "mappers/UserMapper.xml");

        assertThat(configuration.hasStatement(statement(RoleMapper.class, "listByUserId"))).isTrue();
        assertThat(configuration.hasStatement(
                statement(RoleMapper.class, "listPermissionCodesByUserId"))).isTrue();
        assertThat(configuration.hasStatement(
                statement(RoleMapper.class, "listPermissionCodesByRoleId"))).isTrue();
        assertThat(configuration.hasStatement(
                statement(RoleMapper.class, "listPermissionCodesByRoleIds"))).isTrue();
        assertThat(configuration.hasStatement(
                statement(UserMapper.class, "countEnabledEnterpriseAdmins"))).isTrue();
        assertThat(configuration.hasStatement(
                statement(UserMapper.class, "listEnterpriseAdministrators"))).isTrue();
    }

    private void parse(Configuration configuration, String resource) throws IOException {
        try (InputStream inputStream = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(
                    inputStream,
                    configuration,
                    resource,
                    configuration.getSqlFragments())
                    .parse();
        }
    }

    private String statement(Class<?> mapperType, String methodName) {
        return mapperType.getName() + "." + methodName;
    }
}
