package com.gis.servelq.configs;

import com.gis.servelq.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // Public: login and the container probes
                        .requestMatchers("/serveiq/api/auth/login").permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/serveiq/ws/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/serveiq/api/auth/counters").permitAll()

                        // Public: TV content streaming (HLS + direct stream + images)
                        .requestMatchers(HttpMethod.GET, "/serveiq/api/tv-content/stream/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/serveiq/api/tv-content/hls/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/images/**").permitAll()

                        // Public: App config images and dashboard (for TV/Feedback/Kiosk displays)
                        .requestMatchers(HttpMethod.GET, "/serveiq/api/app-config/images/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/serveiq/api/app-config/*/dashboard").permitAll()
                        .requestMatchers(HttpMethod.GET, "/serveiq/api/app-config/templates/*/active").permitAll()

                        // Kiosk, reception and the admin panel issue tickets while
                        // logged in, so the token records who created it.
                        .requestMatchers(HttpMethod.POST, "/serveiq/api/tokens/generate")
                        .hasAnyRole("SUPERVISOR", "RECEPTIONIST", "KIOSK")
                        // The feedback screen is logged in; feedback is credited by its user.
                        .requestMatchers(HttpMethod.POST, "/serveiq/api/feedback", "/serveiq/api/feedback/timeout")
                        .hasAnyRole("ADMIN", "FEEDBACK")
                        .requestMatchers(HttpMethod.GET, "/serveiq/api/tv-display/**").permitAll()
                        // Counter board feed for the Spacehub TV, which has no ServeIQ login.
                        .requestMatchers(HttpMethod.GET, "/serveiq/api/counters/display-board/branch/*").permitAll()
                        .requestMatchers(HttpMethod.GET, "/serveiq/api/news/breaking-news").permitAll()
                        .requestMatchers(HttpMethod.GET, "/serveiq/api/news/images/**").permitAll()

                        // Admin panel staff: ADMIN, MANAGER and SUPERVISOR (see roleHierarchy).
                        // UserService stops them creating or editing anyone at or above their own rank.
                        .requestMatchers("/serveiq/api/auth/register").hasRole("SUPERVISOR")
                        // Any logged-in user may change their own password and FCM token;
                        // the controller checks the FCM id is the caller's own.
                        .requestMatchers(HttpMethod.POST, "/serveiq/api/users/me/password").authenticated()
                        .requestMatchers(HttpMethod.PATCH, "/serveiq/api/users/update-fcm-token/**").authenticated()
                        .requestMatchers(HttpMethod.POST, "/serveiq/api/users/update-fcm-token",
                                "/serveiq/api/users/update-fcm-token/").authenticated()
                        .requestMatchers("/serveiq/api/users/**").hasRole("SUPERVISOR")
                        // Login History: admin and manager. Audit log: admin only.
                        .requestMatchers("/serveiq/api/admin/user-sessions/**").hasRole("MANAGER")
                        .requestMatchers("/serveiq/api/admin/audit-logs/**").hasRole("ADMIN")
                        .requestMatchers("/serveiq/api/admin/ad-sync/**").hasRole("ADMIN")
                        .requestMatchers("/api/admin/**", "/serveiq/api/admin/**").hasRole("SUPERVISOR")
                        .requestMatchers("/serveiq/api/reports/**").hasRole("SUPERVISOR")
                        .requestMatchers("/serveiq/api/branches/**").hasRole("SUPERVISOR")
                        .requestMatchers("/serveiq/api/whatsapp/**").hasRole("ADMIN")
                        .requestMatchers("/serveiq/api/health-check").permitAll()
                        // Serving customers
                        .requestMatchers(HttpMethod.POST, "/serveiq/api/transfer-reasons/**").hasRole("SUPERVISOR")
                        .requestMatchers(HttpMethod.PUT, "/serveiq/api/transfer-reasons/**").hasRole("SUPERVISOR")
                        .requestMatchers(HttpMethod.DELETE, "/serveiq/api/transfer-reasons/**").hasRole("SUPERVISOR")
                        .requestMatchers("/serveiq/api/agent/**")
                        .hasAnyRole("SUPERVISOR", "USER", "RECEPTIONIST")
                        .requestMatchers("/serveiq/api/counters/**")
                        .hasAnyRole("SUPERVISOR", "USER", "RECEPTIONIST", "DISPLAY", "FEEDBACK")
                        .anyRequest().authenticated())

                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * ADMIN holds every other role, so the admin can call any API, both the URL
     * rules above and the @PreAuthorize checks. MANAGER holds SUPERVISOR, so a
     * rule for SUPERVISOR admits all admin panel staff; one for MANAGER leaves
     * the supervisor out.
     */
    @Bean
    public RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.withDefaultRolePrefix()
                .role("ADMIN").implies("MANAGER", "SUPERVISOR", "USER", "DISPLAY", "KIOSK", "RECEPTIONIST", "FEEDBACK")
                .role("MANAGER").implies("SUPERVISOR")
                .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Authorization"));
        config.setAllowCredentials(false); // must be false when using "*" for origins
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}