package com.gis.servelq.repository;

import com.gis.servelq.models.User;
import com.gis.servelq.models.UserRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, String> {

    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    List<User> findByRole(UserRole role);
    Page<User> findByRole(UserRole role, Pageable pageable);

    Page<User> findByNameContainingIgnoreCaseOrEmailContainingIgnoreCase(
            String name, String email, Pageable pageable);

    @Query("SELECT u FROM User u WHERE " +
            "(:search IS NULL OR LOWER(u.name) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%'))) AND " +
            "(:role IS NULL OR u.role = :role)")
    Page<User> searchUsers(@Param("search") String search,
                           @Param("role") UserRole role,
                           Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    // Device accounts (TV, kiosk, feedback screen) are left signed in: they
    // log in once and have nobody to log them back in each morning.
    @Query("UPDATE User u SET u.tokensValidAfter = :cutoff WHERE u.role NOT IN :skip")
    int revokeTokensExcept(@Param("cutoff") LocalDateTime cutoff,
                           @Param("skip") java.util.Collection<UserRole> skip);

    default int revokeAllTokens(LocalDateTime cutoff) {
        return revokeTokensExcept(cutoff, java.util.List.of(UserRole.DISPLAY, UserRole.KIOSK, UserRole.FEEDBACK));
    }

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    // A feedback screen's counterId is its fixed assignment, not a held
    // counter, so the daily sign-out keeps it.
    @Query("UPDATE User u SET u.counterId = NULL WHERE u.counterId IS NOT NULL AND u.role <> :keep")
    int clearCounterIdsExcept(@Param("keep") UserRole keep);

    default int clearAllCounterIds() {
        return clearCounterIdsExcept(UserRole.FEEDBACK);
    }
}
