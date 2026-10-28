package com.gis.servelq.models;

public enum UserRole {
    ADMIN, USER, MANAGER, DISPLAY, KIOSK, RECEPTIONIST, FEEDBACK, SUPERVISOR, AGENT;

    /** USER and AGENT both serve at a counter and follow the same rules. */
    public boolean isCounterAgent() {
        return this == USER || this == AGENT;
    }

    public static boolean isCounterAgent(String role) {
        return USER.name().equals(role) || AGENT.name().equals(role);
    }
}
