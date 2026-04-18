package com.reportai.hub.common.context;

import java.util.List;

public final class UserContext {

    private static final ThreadLocal<Long> USER_ID_HOLDER = new ThreadLocal<>();
    private static final ThreadLocal<String> USERNAME_HOLDER = new ThreadLocal<>();
    private static final ThreadLocal<Long> TENANT_ID_HOLDER = new ThreadLocal<>();
    private static final ThreadLocal<List<String>> ROLES_HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void setUserId(Long id) {
        USER_ID_HOLDER.set(id);
    }

    public static Long getUserId() {
        return USER_ID_HOLDER.get();
    }

    public static void setUsername(String name) {
        USERNAME_HOLDER.set(name);
    }

    public static String getUsername() {
        return USERNAME_HOLDER.get();
    }

    public static void setTenantId(Long id) {
        TENANT_ID_HOLDER.set(id);
    }

    public static Long getTenantId() {
        return TENANT_ID_HOLDER.get();
    }

    public static void setRoles(List<String> roles) {
        ROLES_HOLDER.set(roles);
    }

    public static List<String> getRoles() {
        return ROLES_HOLDER.get();
    }

    public static void clear() {
        USER_ID_HOLDER.remove();
        USERNAME_HOLDER.remove();
        TENANT_ID_HOLDER.remove();
        ROLES_HOLDER.remove();
    }
}
