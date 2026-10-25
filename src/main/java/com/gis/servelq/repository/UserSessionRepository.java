package com.gis.servelq.repository;

import com.gis.servelq.models.LogoutType;
import com.gis.servelq.models.UserRole;
import com.gis.servelq.models.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface UserSessionRepository extends JpaRepository<UserSession, String>,
        JpaSpecificationExecutor<UserSession> {

    List<UserSession> findByUserIdOrderByLoginAtDesc(String userId);

    // Sessions of one role in a branch that overlap [from, to)
    @Query("SELECT s FROM UserSession s WHERE s.branchId = :branchId AND s.role = :role " +
            "AND s.loginAt < :to AND (s.logoutAt IS NULL OR s.logoutAt > :from)")
    List<UserSession> findOverlapping(@Param("branchId") String branchId,
                                      @Param("role") UserRole role,
                                      @Param("from") LocalDateTime from,
                                      @Param("to") LocalDateTime to);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE UserSession s SET s.logoutAt = :at, s.logoutType = :type " +
            "WHERE s.userId = :userId AND s.logoutAt IS NULL")
    int closeOpenSessions(@Param("userId") String userId,
                          @Param("at") LocalDateTime at,
                          @Param("type") LogoutType type);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE UserSession s SET s.logoutAt = :at, s.logoutType = :type WHERE s.logoutAt IS NULL")
    int closeAllOpenSessions(@Param("at") LocalDateTime at, @Param("type") LogoutType type);
}
