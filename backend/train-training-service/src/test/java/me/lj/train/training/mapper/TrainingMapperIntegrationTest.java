package me.lj.train.training.mapper;

import com.mybatisflex.core.query.QueryWrapper;
import me.lj.train.training.model.entity.CourseEntity;
import me.lj.train.training.model.entity.CoursewareEntity;
import me.lj.train.training.model.entity.StorageObjectEntity;
import me.lj.train.training.model.entity.UploadSessionEntity;
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

import java.time.LocalDateTime;
import java.util.List;

import static me.lj.train.training.model.table.CourseTableDef.COURSE;
import static me.lj.train.training.model.table.CoursewareTableDef.COURSEWARE;
import static me.lj.train.training.model.table.StorageObjectTableDef.STORAGE_OBJECT;
import static me.lj.train.training.model.table.UploadSessionTableDef.UPLOAD_SESSION;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 培训库Flyway与Mapper真实MySQL集成测试。
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
        classes = TrainingMapperIntegrationTest.MapperTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.config.name=training-mapper-integration-test",
                "spring.application.name=training-mapper-integration-test",
                "spring.flyway.enabled=true",
                "spring.flyway.locations=classpath:db/migration",
                "mybatis-flex.configuration.map-underscore-to-camel-case=true"
        })
class TrainingMapperIntegrationTest {

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("road_training_training_test")
            .withUsername("train_training_test")
            .withPassword("train_training_test")
            .withUrlParam("serverTimezone", "Asia/Shanghai")
            .withUrlParam("useUnicode", "true")
            .withUrlParam("characterEncoding", "UTF-8");

    @Autowired
    private CourseMapper courseMapper;
    @Autowired
    private CoursewareMapper coursewareMapper;
    @Autowired
    private StorageObjectMapper storageObjectMapper;
    @Autowired
    private UploadSessionMapper uploadSessionMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
    }

    @Test
    void shouldMigrateCourseAndPlanTenantTablesWithIndexesAndNoForeignKeys() {
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables "
                        + "WHERE table_schema = DATABASE() AND table_name LIKE 'train_%' "
                        + "ORDER BY table_name",
                String.class);
        List<String> indexes = jdbcTemplate.queryForList(
                "SELECT DISTINCT index_name FROM information_schema.statistics "
                        + "WHERE table_schema = DATABASE() AND table_name IN "
                        + "('train_course', 'train_courseware', 'train_storage_object', "
                        + "'train_upload_session', 'train_plan', 'train_plan_course', "
                        + "'train_plan_courseware_snapshot', 'train_plan_user')",
                String.class);
        Integer foreignKeyCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.referential_constraints "
                        + "WHERE constraint_schema = DATABASE()",
                Integer.class);
        Integer consumeLogCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables "
                        + "WHERE table_schema = DATABASE() AND table_name = 'mq_consume_log'",
                Integer.class);

        assertThat(tables).containsExactly(
                "train_course",
                "train_courseware",
                "train_plan",
                "train_plan_course",
                "train_plan_courseware_snapshot",
                "train_plan_user",
                "train_storage_object",
                "train_upload_session");
        assertThat(indexes).contains(
                "idx_course_enterprise_status",
                "idx_courseware_course_order",
                "idx_storage_enterprise_status",
                "idx_upload_enterprise_course",
                "idx_upload_expiry",
                "idx_plan_enterprise_status",
                "idx_plan_course_order",
                "idx_plan_courseware_order",
                "idx_plan_user_student");
        assertThat(consumeLogCount).isEqualTo(1);
        assertThat(foreignKeyCount).isZero();
    }

    @Test
    void shouldPersistCourseAssetsAndIsolateMapperQueriesByEnterprise() {
        CourseEntity first = course(100L, 20L, "企业一课程");
        CourseEntity second = course(101L, 21L, "企业二课程");
        assertThat(courseMapper.insertSelective(first)).isEqualTo(1);
        assertThat(courseMapper.insertSelective(second)).isEqualTo(1);

        StorageObjectEntity object = storageObject();
        CoursewareEntity courseware = courseware();
        UploadSessionEntity session = uploadSession();
        assertThat(storageObjectMapper.insertSelective(object)).isEqualTo(1);
        assertThat(coursewareMapper.insertSelective(courseware)).isEqualTo(1);
        assertThat(uploadSessionMapper.insertSelective(session)).isEqualTo(1);

        assertThat(courseMapper.selectListByQuery(QueryWrapper.create()
                .where(COURSE.ENTERPRISE_ID.eq(20L))))
                .extracting(CourseEntity::getId)
                .containsExactly(100L);
        assertThat(coursewareMapper.selectCountByQuery(QueryWrapper.create()
                .where(COURSEWARE.ENTERPRISE_ID.eq(20L))
                .and(COURSEWARE.COURSE_ID.eq(100L)))).isEqualTo(1);
        assertThat(storageObjectMapper.selectCountByQuery(QueryWrapper.create()
                .where(STORAGE_OBJECT.ENTERPRISE_ID.eq(20L))
                .and(STORAGE_OBJECT.STATUS.eq("ACTIVE")))).isEqualTo(1);
        assertThat(uploadSessionMapper.selectCountByQuery(QueryWrapper.create()
                .where(UPLOAD_SESSION.ENTERPRISE_ID.eq(20L))
                .and(UPLOAD_SESSION.STATUS.eq("INITIATED")))).isEqualTo(1);
    }

    private CourseEntity course(Long id, Long enterpriseId, String name) {
        CourseEntity course = new CourseEntity();
        course.setId(id);
        course.setEnterpriseId(enterpriseId);
        course.setCourseName(name);
        course.setRequiredDurationSeconds(60);
        course.setAllowSeek(false);
        course.setProgressReportIntervalSeconds(20);
        course.setStudyToleranceSeconds(30);
        course.setStatus("DRAFT");
        course.setEverEnabled(false);
        course.setCreatedBy(10L);
        course.setUpdatedBy(10L);
        return course;
    }

    private StorageObjectEntity storageObject() {
        StorageObjectEntity object = new StorageObjectEntity();
        object.setId(200L);
        object.setEnterpriseId(20L);
        object.setProvider("ALIYUN_OSS");
        object.setBucketName("integration-bucket");
        object.setObjectKey("enterprises/20/courses/100/video.mp4");
        object.setOriginalFilename("video.mp4");
        object.setObjectType("VIDEO");
        object.setContentType("video/mp4");
        object.setFileSize(1024L);
        object.setEtag("etag");
        object.setStatus("ACTIVE");
        object.setCreatedBy(10L);
        object.setUpdatedBy(10L);
        return object;
    }

    private CoursewareEntity courseware() {
        CoursewareEntity courseware = new CoursewareEntity();
        courseware.setId(300L);
        courseware.setEnterpriseId(20L);
        courseware.setCourseId(100L);
        courseware.setStorageObjectId(200L);
        courseware.setCoursewareTitle("第一章");
        courseware.setDurationSeconds(60);
        courseware.setSortOrder(1);
        courseware.setCreatedBy(10L);
        courseware.setUpdatedBy(10L);
        return courseware;
    }

    private UploadSessionEntity uploadSession() {
        UploadSessionEntity session = new UploadSessionEntity();
        session.setId(400L);
        session.setEnterpriseId(20L);
        session.setCourseId(100L);
        session.setStorageObjectId(201L);
        session.setCoursewareId(301L);
        session.setUploadType("VIDEO");
        session.setBucketName("integration-bucket");
        session.setObjectKey("enterprises/20/courses/100/upload.mp4");
        session.setOssUploadId("upload-id");
        session.setOriginalFilename("upload.mp4");
        session.setExpectedContentType("video/mp4");
        session.setExpectedFileSize(1024L);
        session.setClientLastModified(1234L);
        session.setVideoDurationSeconds(60);
        session.setCoursewareTitle("第二章");
        session.setPartSizeBytes(8_388_608L);
        session.setPartCount(1);
        session.setStatus("INITIATED");
        session.setExpiresAt(LocalDateTime.now().plusHours(24));
        session.setCreatedBy(10L);
        session.setUpdatedBy(10L);
        return session;
    }

    /** 仅装配Flyway、数据源及MyBatis-Flex。 */
    @SpringBootConfiguration(proxyBeanMethods = false)
    @ImportAutoConfiguration({
            DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class,
            FlywayAutoConfiguration.class,
            com.mybatisflex.spring.boot.MybatisFlexAutoConfiguration.class
    })
    @MapperScan("me.lj.train.training.mapper")
    static class MapperTestApplication {
    }
}
