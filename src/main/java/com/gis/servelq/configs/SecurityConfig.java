package com.gis.servelq.configs;

import com.gis.servelq.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
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

import java.util.Arrays;
import java.util.List;

/**
 * The chain was anyRequest().permitAll() and login handed back no token, so
 * nothing was ever actually authenticated. This denies by default and opens up
 * only what has to be reachable without an account.
 */
@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // Stateless bearer tokens, no cookies, so CSRF does not apply.
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

                        // Walk-up kiosk: a visitor takes a ticket without an
                        // account and rates the service afterwards. The lobby TV
                        // also has no login.
                        .requestMatchers(HttpMethod.POST, "/serveiq/api/tokens/generate").permitAll()
                        .requestMatchers(HttpMethod.POST, "/serveiq/api/feedback").permitAll()
                        .requestMatchers(HttpMethod.GET, "/serveiq/api/tv-display/**").permitAll()

                        // Creating accounts is an admin job. Registration used to
                        // be open and honoured the role from the body, so anyone
                        // could POST {"role":"ADMIN"} and become one.
                        .requestMatchers("/serveiq/api/auth/register").hasRole("ADMIN")
                        .requestMatchers("/serveiq/api/users/**").hasAnyRole("ADMIN","MANAGER")
                        .requestMatchers("/api/admin/**", "/serveiq/api/admin/**").hasAnyRole("ADMIN","MANAGER")
                        .requestMatchers("/serveiq/api/reports/**").hasAnyRole("ADMIN", "MANAGER")
                        .requestMatchers("/serveiq/api/branches/**").hasAnyRole("ADMIN", "MANAGER")


                        // Outbound WhatsApp was wide open, so anyone could push
                        // messages through the Twilio account at our cost.
                        .requestMatchers("/serveiq/api/whatsapp/**").hasRole("ADMIN")

                        // Serving customers
                        .requestMatchers("/serveiq/api/agent/**")
                            .hasAnyRole("ADMIN", "MANAGER", "USER", "RECEPTIONIST")
                        .requestMatchers("/serveiq/api/counters/**")
                            .hasAnyRole("ADMIN", "MANAGER", "USER", "RECEPTIONIST", "DISPLAY")

                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        // Was allowedOriginPatterns("*") together with allowCredentials(true),
        // which reflects back whatever Origin the caller sends.
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(o -> !o.isEmpty())
                .toList();
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "Origin"));
        config.setExposedHeaders(List.of("Authorization"));
        config.setAllowCredentials(true);
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
