package me.lj.train.learning.mapper;

import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 学习库迁移与关键唯一约束的真实 MySQL 集成测试。 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
        classes = LearningMapperIntegrationTest.MapperTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.config.name=learning-mapper-integration-test",
                "spring.application.name=learning-mapper-integration-test",
                "spring.flyway.enabled=true",
                "spring.flyway.locations=classpath:db/migration",
                "mybatis-flex.configuration.map-underscore-to-camel-case=true"
        })
class LearningMapperIntegrationTest {

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("road_training_learning_test")
            .withUsername("train_learning_test")
            .withPassword("train_learning_test")
            .withUrlParam("serverTimezone", "Asia/Shanghai")
            .withUrlParam("useUnicode", "true")
            .withUrlParam("characterEncoding", "UTF-8");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 注入 Testcontainers 提供的临时 MySQL。 */
    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
    }

    @Test
    void shouldMigrateLearningTablesWithIdempotencyAndActiveSessionIndexes() {
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables "
                        + "WHERE table_schema = DATABASE() AND "
                        + "(table_name LIKE 'study_%' OR table_name = 'mq_outbox') "
                        + "ORDER BY table_name",
                String.class);
        List<String> uniqueIndexes = jdbcTemplate.queryForList(
                "SELECT DISTINCT index_name FROM information_schema.statistics "
                        + "WHERE table_schema = DATABASE() AND non_unique = 0",
                String.class);
        Integer foreignKeyCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.referential_constraints "
                        + "WHERE constraint_schema = DATABASE()",
                Integer.class);

        assertThat(tables).containsExactly(
                "mq_outbox",
                "study_courseware_progress",
                "study_event_log",
                "study_progress",
                "study_session");
        assertThat(uniqueIndexes).contains(
                "uk_study_session_active_user",
                "uk_study_progress_course",
                "uk_courseware_progress",
                "uk_study_event_request",
                "uk_study_event_sequence",
                "uk_outbox_event",
                "uk_outbox_business");
        assertThat(foreignKeyCount).isZero();
    }

    /** Mapper 测试仅装配数据库相关组件，避免连接 Nacos、Dubbo 和 RabbitMQ。 */
    @SpringBootConfiguration(proxyBeanMethods = false)
    @ImportAutoConfiguration({
            DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class,
            FlywayAutoConfiguration.class,
            com.mybatisflex.spring.boot.MybatisFlexAutoConfiguration.class
    })
    @MapperScan("me.lj.train.learning.mapper")
    static class MapperTestApplication {
    }
}
