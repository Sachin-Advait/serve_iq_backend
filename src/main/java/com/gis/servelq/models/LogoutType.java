package com.gis.servelq.models;

/** How a user session (see UserSession) ended. */
public enum LogoutType {
    /** The user logged out themself. */
    MANUAL,
    /** Closed by the daily sign-out (DailyLogoutScheduler). */
    AUTO,
    /** The user logged in again without logging out first. */
    NEW_LOGIN
}
