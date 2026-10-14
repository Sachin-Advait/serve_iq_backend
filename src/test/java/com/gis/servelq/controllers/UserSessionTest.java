package com.gis.servelq.controllers;

import com.gis.servelq.models.LogoutType;
import com.gis.servelq.models.User;
import com.gis.servelq.models.UserRole;
import com.gis.servelq.models.UserSession;
import com.gis.servelq.repository.UserRepository;
import com.gis.servelq.repository.UserSessionRepository;
import com.gis.servelq.security.JwtService;
import com.gis.servelq.services.DailyLogoutScheduler;
import com.gis.servelq.services.UserSessionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Check-in / check-out history: a row per login, closed by the user's own logout,
 * a new login, or the daily sign-out (which leaves already closed rows alone).
 */
@SpringBootTest(properties = {
        "ad.ldap.urls=ldap://localhost:1",
        "ad.ldap.bind-user=test",
        "ad.ldap.bind-password=test",
        "ad.ldap.user-search-base=dc=test",
        "ad.ldap.user-search-filter=(uid={0})",
        "image.upload.dir=target/test-images",
        "app.daily-logout.cron=-"
})
@AutoConfigureMockMvc
class UserSessionTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired UserSessionRepository sessions;
    @Autowired UserSessionService userSessionService;
    @Autowired DailyLogoutScheduler dailyLogout;
    @Autowired JwtService jwt;

    @Test
    void loginAgainClosesThePreviousSession() {
        User admin = user(UserRole.ADMIN);
        userSessionService.checkIn(admin);
        userSessionService.checkIn(admin);

        List<UserSession> rows = sessions.findByUserIdOrderByLoginAtDesc(admin.getId());
        assertThat(rows).hasSize(2);
        assertThat(rows).filteredOn(s -> s.getLogoutAt() == null).hasSize(1);
        assertThat(rows).filteredOn(s -> s.getLogoutType() == LogoutType.NEW_LOGIN).hasSize(1);
    }

    @Test
    void manualLogoutIsKeptAndDailySignOutClosesOnlyOpenSessions() throws Exception {
        User receptionist = user(UserRole.RECEPTIONIST);
        User admin = user(UserRole.ADMIN);
        userSessionService.checkIn(receptionist);
        userSessionService.checkIn(admin);

        mvc.perform(post("/serveiq/api/auth/logout")
                        .header("Authorization", "Bearer " + jwt.generateToken(receptionist)))
                .andExpect(status().isNoContent());
        UserSession manual = sessions.findByUserIdOrderByLoginAtDesc(receptionist.getId()).get(0);
        assertThat(manual.getLogoutType()).isEqualTo(LogoutType.MANUAL);

        dailyLogout.signOutEveryone();

        UserSession stillManual = sessions.findById(manual.getId()).orElseThrow();
        assertThat(stillManual.getLogoutType()).isEqualTo(LogoutType.MANUAL);
        assertThat(stillManual.getLogoutAt()).isEqualTo(manual.getLogoutAt());
        UserSession auto = sessions.findByUserIdOrderByLoginAtDesc(admin.getId()).get(0);
        assertThat(auto.getLogoutType()).isEqualTo(LogoutType.AUTO);
        assertThat(auto.getLogoutAt()).isNotNull();
    }

    @Test
    void dailySignOutSignsOutReceptionistsButNotFeedbackScreens() {
        User receptionist = user(UserRole.RECEPTIONIST);
        User feedback = user(UserRole.FEEDBACK);

        dailyLogout.signOutEveryone();

        assertThat(users.findById(receptionist.getId()).orElseThrow().getTokensValidAfter()).isNotNull();
        assertThat(users.findById(feedback.getId()).orElseThrow().getTokensValidAfter()).isNull();
    }

    @Test
    void deviceAccountsAreNotTracked() {
        User kiosk = user(UserRole.KIOSK);
        userSessionService.checkIn(kiosk);
        assertThat(sessions.findByUserIdOrderByLoginAtDesc(kiosk.getId())).isEmpty();
    }

    private User user(UserRole role) {
        User u = new User();
        u.setEmail(role.name().toLowerCase() + "-" + UUID.randomUUID().toString().substring(0, 8) + "@test.local");
        u.setName(role.name());
        u.setPassword("x");
        u.setRole(role);
        return users.save(u);
    }
}
