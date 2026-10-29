package com.gis.servelq.models;

import java.util.EnumSet;
import java.util.Set;

/**
 * The client app a login comes from, and the roles that may sign in to it.
 * Apps send it as "app" on auth/login; a login without it (admin panel) is not checked here.
 */
public enum LoginApp {
    TV(EnumSet.of(UserRole.DISPLAY)),
    FEEDBACK(EnumSet.of(UserRole.FEEDBACK)),
    KIOSK(EnumSet.of(UserRole.KIOSK, UserRole.RECEPTIONIST)),
    AGENT(EnumSet.of(UserRole.AGENT));

    private final Set<UserRole> allowedRoles;

    LoginApp(Set<UserRole> allowedRoles) {
        this.allowedRoles = allowedRoles;
    }

    public boolean allows(UserRole role) {
        return allowedRoles.contains(role);
    }

    /** Null for an unknown name. */
    public static LoginApp from(String name) {
        for (LoginApp app : values()) {
            if (app.name().equalsIgnoreCase(name.trim())) return app;
        }
        return null;
    }
}
