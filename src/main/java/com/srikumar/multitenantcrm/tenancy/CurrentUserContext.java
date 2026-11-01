package com.srikumar.multitenantcrm.tenancy;

import java.util.UUID;

/** Holds the current request's user id (from the JWT's user_id claim), for audit log actor_id. */
public final class CurrentUserContext {

    private static final ThreadLocal<UUID> CURRENT_USER = new ThreadLocal<>();

    private CurrentUserContext() {
    }

    public static void setUserId(UUID userId) {
        CURRENT_USER.set(userId);
    }

    public static UUID getUserId() {
        return CURRENT_USER.get();
    }

    public static void clear() {
        CURRENT_USER.remove();
    }
}
