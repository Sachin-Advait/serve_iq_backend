package com.gis.servelq.services;

import com.gis.servelq.dto.PageResponseDTO;
import com.gis.servelq.dto.UserSessionDTO;
import com.gis.servelq.models.Counter;
import com.gis.servelq.models.LogoutType;
import com.gis.servelq.models.User;
import com.gis.servelq.models.UserRole;
import com.gis.servelq.models.UserSession;
import com.gis.servelq.repository.CounterRepository;
import com.gis.servelq.repository.UserRepository;
import com.gis.servelq.repository.UserSessionRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Keeps the check-in / check-out history (user_sessions) of agents, admins and
 * receptionists. Device accounts (TV, kiosk, feedback screen) and managers are not tracked.
 */
@Service
@RequiredArgsConstructor
public class UserSessionService {

    private static final Set<UserRole> TRACKED = Set.of(UserRole.USER, UserRole.AGENT, UserRole.ADMIN, UserRole.RECEPTIONIST,
            UserRole.MANAGER, UserRole.SUPERVISOR);

    private final UserSessionRepository userSessionRepository;
    private final UserRepository userRepository;
    private final CounterRepository counterRepository;

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

    /**
     * Login history for the admin panel, newest check-in first. Every filter is optional;
     * the dates are inclusive days matched against the check-in time.
     */
    @Transactional(readOnly = true)
    public PageResponseDTO<UserSessionDTO> search(String branchId, String userId, UserRole role,
                                                  LocalDate from, LocalDate to, int page, int size) {
        Specification<UserSession> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (StringUtils.hasText(branchId)) p.add(cb.equal(root.get("branchId"), branchId));
            if (StringUtils.hasText(userId)) p.add(cb.equal(root.get("userId"), userId));
            if (role != null) p.add(cb.equal(root.get("role"), role));
            if (from != null) p.add(cb.greaterThanOrEqualTo(root.get("loginAt"), from.atStartOfDay()));
            if (to != null) p.add(cb.lessThan(root.get("loginAt"), to.plusDays(1).atStartOfDay()));
            return cb.and(p.toArray(new Predicate[0]));
        };
        Page<UserSession> sessions = userSessionRepository.findAll(spec,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 200),
                        Sort.by(Sort.Direction.DESC, "loginAt")));

        Map<String, User> users = userRepository.findAllById(
                        sessions.stream().map(UserSession::getUserId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        Map<String, Counter> counters = counterRepository.findAllById(
                        sessions.stream().map(UserSession::getCounterId)
                                .filter(StringUtils::hasText).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Counter::getId, Function.identity()));

        return new PageResponseDTO<>(sessions.map(s -> {
            User u = users.get(s.getUserId());
            Counter c = s.getCounterId() == null ? null : counters.get(s.getCounterId());
            return UserSessionDTO.builder()
                    .id(s.getId())
                    .userId(s.getUserId())
                    .userName(u == null ? null : u.getName())
                    .userEmail(u == null ? null : u.getEmail())
                    .role(s.getRole())
                    .branchId(s.getBranchId())
                    .counterId(s.getCounterId())
                    .counterName(c == null ? null : c.getName())
                    .loginAt(s.getLoginAt())
                    .logoutAt(s.getLogoutAt())
                    .logoutType(s.getLogoutType())
                    .durationMinutes(s.getLogoutAt() == null ? null
                            : Duration.between(s.getLoginAt(), s.getLogoutAt()).toMinutes())
                    .build();
        }));
    }
}
