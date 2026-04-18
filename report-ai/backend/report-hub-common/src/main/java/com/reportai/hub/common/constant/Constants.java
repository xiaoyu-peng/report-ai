package com.reportai.hub.common.constant;

/**
 * 系统常量定义类
 * 
 * @author skill-hub
 */
public final class Constants {

    private Constants() {
    }

    public static final String DEFAULT_TENANT_ID = "default";

    public static final String PERMISSION_PUBLIC = "PUBLIC";
    public static final String PERMISSION_DEPARTMENT = "DEPARTMENT";
    public static final String PERMISSION_PRIVATE = "PRIVATE";

    public static final String STATUS_ENABLED = "ENABLED";
    public static final String STATUS_DISABLED = "DISABLED";
    public static final String STATUS_DRAFT = "draft";
    public static final String STATUS_PUBLISHED = "published";
    public static final String STATUS_ARCHIVED = "archived";

    public static final String SKILL_TYPE_SKILL = "SKILL";
    public static final String SKILL_TYPE_MCP = "MCP";

    public static final String ROLE_SUPER_ADMIN = "SUPER_ADMIN";
    public static final String ROLE_TENANT_ADMIN = "TENANT_ADMIN";
    public static final String ROLE_DEPT_ADMIN = "DEPT_ADMIN";
    public static final String ROLE_USER = "USER";

    public static final String ACTION_CREATE = "CREATE";
    public static final String ACTION_UPDATE = "UPDATE";
    public static final String ACTION_DELETE = "DELETE";
    public static final String ACTION_ENABLE = "ENABLE";
    public static final String ACTION_DISABLE = "DISABLE";
    public static final String ACTION_IMPORT = "IMPORT";
    public static final String ACTION_EXPORT = "EXPORT";
    public static final String ACTION_APPROVE = "APPROVE";
    public static final String ACTION_REJECT = "REJECT";
}
