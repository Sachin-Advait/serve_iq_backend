package com.gis.servelq.services;

import com.gis.servelq.models.LogoutType;
import com.gis.servelq.models.User;
import com.gis.servelq.models.UserRole;
import com.gis.servelq.models.UserSession;
import com.gis.servelq.repository.UserSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * Keeps the check-in / check-out history (user_sessions) of agents, admins and
 * receptionists. Device accounts (TV, kiosk, feedback screen) and managers are not tracked.
 */
@Service
@RequiredArgsConstructor
public class UserSessionService {

    private static final Set<UserRole> TRACKED = Set.of(UserRole.USER, UserRole.ADMIN, UserRole.RECEPTIONIST);

    private final UserSessionRepository userSessionRepository;

    public static boolean isTracked(UserRole role) {
        return role != null && TRACKED.contains(role);
    }

    /** Opens a session row; a session left open by an earlier login is closed as NEW_LOGIN. */
    @Transactional
    public void checkIn(User user) {
        if (!isTracked(user.getRole())) return;
        LocalDateTime now = LocalDateTime.now();
        userSessionRepository.closeOpenSessions(user.getId(), now, LogoutType.NEW_LOGIN);

        UserSession session = new UserSession();
        session.setUserId(user.getId());
        session.setRole(user.getRole());
        session.setBranchId(user.getBranchId());
        session.setCounterId(user.getCounterId());
        session.setLoginAt(now);
        userSessionRepository.save(session);
    }

    /** The user logged out themself. */
    @Transactional
    public void checkOut(String userId) {
        if (userId == null) return;
        userSessionRepository.closeOpenSessions(userId, LocalDateTime.now(), LogoutType.MANUAL);
    }

    /** Daily sign-out: closes only the sessions still open. */
    @Transactional
    public int checkOutAllOpen() {
        return userSessionRepository.closeAllOpenSessions(LocalDateTime.now(), LogoutType.AUTO);
    }
}
