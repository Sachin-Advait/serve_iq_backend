package com.gis.servelq.models;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LoginAppTest {

    @Test
    void eachAppAllowsOnlyItsRoles() {
        assertTrue(LoginApp.TV.allows(UserRole.DISPLAY));
        assertFalse(LoginApp.TV.allows(UserRole.ADMIN));
        assertTrue(LoginApp.FEEDBACK.allows(UserRole.FEEDBACK));
        assertFalse(LoginApp.FEEDBACK.allows(UserRole.AGENT));
        assertTrue(LoginApp.KIOSK.allows(UserRole.KIOSK));
        assertTrue(LoginApp.KIOSK.allows(UserRole.RECEPTIONIST));
        assertFalse(LoginApp.KIOSK.allows(UserRole.DISPLAY));
        assertTrue(LoginApp.AGENT.allows(UserRole.AGENT));
        assertFalse(LoginApp.AGENT.allows(UserRole.USER));
    }

    @Test
    void parsesNamesIgnoringCase() {
        assertEquals(LoginApp.KIOSK, LoginApp.from(" kiosk "));
        assertNull(LoginApp.from("ADMIN_PANEL"));
    }
}
