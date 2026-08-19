package me.lj.train.training.constant;

/**
 * 课程管理权限编码。
 */
public final class TrainingPermissions {

    public static final String COURSE_VIEW = "admin:course:view";
    public static final String COURSE_CREATE = "admin:course:create";
    public static final String COURSE_UPDATE = "admin:course:update";
    public static final String COURSE_STATUS = "admin:course:status";
    public static final String COURSE_DELETE = "admin:course:delete";
    public static final String COURSEWARE_MANAGE = "admin:courseware:manage";
    public static final String PLAN_VIEW = "admin:plan:view";
    public static final String PLAN_CREATE = "admin:plan:create";
    public static final String PLAN_UPDATE = "admin:plan:update";
    public static final String PLAN_PUBLISH = "admin:plan:publish";
    public static final String PLAN_CANCEL = "admin:plan:cancel";
    public static final String STUDENT_PLAN_VIEW = "student:plan:view";
    public static final String STUDENT_LEARNING_STUDY = "student:learning:study";

    private TrainingPermissions() {
    }
}
