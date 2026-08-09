package com.gis.servelq.security;

import com.gis.servelq.models.User;
import com.gis.servelq.models.UserRole;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "unit-test-signing-key-long-enough-for-hs256";

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(SECRET, 60);
        jwtService.init();
    }

    private User user(String id, UserRole role, String branchId) {
        User u = new User();
        u.setId(id);
        u.setEmail(id + "@example.com");
        u.setRole(role);
        u.setBranchId(branchId);
        return u;
    }

    @Test
    void tokenCarriesIdRoleEmailAndBranch() {
        Claims claims = jwtService.parse(
                jwtService.generateToken(user("agent-1", UserRole.USER, "branch-7")));

        assertThat(claims).isNotNull();
        assertThat(claims.getSubject()).isEqualTo("agent-1");
        assertThat(claims.get("role", String.class)).isEqualTo("USER");
        assertThat(claims.get("branchId", String.class)).isEqualTo("branch-7");
    }

    @Test
    void parseReturnsNullForGarbage() {
        assertThat(jwtService.parse("nonsense")).isNull();
    }

    /**
     * Without signature verification anyone could hand themselves an ADMIN
     * token, which is exactly what the role claim controls.
     */
    @Test
    void tokenSignedWithAnotherKeyIsRejected() {
        JwtService other = new JwtService("a-completely-different-signing-key-value", 60);
        other.init();

        assertThat(jwtService.parse(other.generateToken(user("x", UserRole.ADMIN, null)))).isNull();
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        JwtService shortLived = new JwtService(SECRET, 0);
        shortLived.init();

        String token = shortLived.generateToken(user("agent-2", UserRole.USER, "b-1"));
        Thread.sleep(1100);

        assertThat(shortLived.parse(token)).isNull();
    }

    @Test
    void shortSecretIsRejectedAtStartup() {
        assertThatThrownBy(() -> new JwtService("short", 60).init())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 characters");
    }
}
