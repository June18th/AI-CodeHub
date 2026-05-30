package com.aicodehub.common;

public final class UserContext {
    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> USER_ROLE = new ThreadLocal<>();

    private UserContext() {}

    public static void set(Long id, String role) {
        USER_ID.set(id);
        USER_ROLE.set(role);
    }

    public static Long getUserId() { return USER_ID.get(); }
    public static String getRole() { return USER_ROLE.get(); }

    public static void clear() {
        USER_ID.remove();
        USER_ROLE.remove();
    }
}
