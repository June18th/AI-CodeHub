package com.aicodehub.common;

public final class UserContext {
    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> USER_ROLE = new ThreadLocal<>();
    private static final ThreadLocal<String> ORG_TAGS = new ThreadLocal<>();

    private UserContext() {}

    public static void set(Long id, String role) { set(id, role, null); }

    public static void set(Long id, String role, String orgTags) {
        USER_ID.set(id);
        USER_ROLE.set(role);
        ORG_TAGS.set(orgTags);
    }

    public static Long getUserId() { return USER_ID.get(); }
    public static String getRole() { return USER_ROLE.get(); }
    public static String getOrgTags() { return ORG_TAGS.get(); }

    public static void clear() {
        USER_ID.remove();
        USER_ROLE.remove();
        ORG_TAGS.remove();
    }
}
