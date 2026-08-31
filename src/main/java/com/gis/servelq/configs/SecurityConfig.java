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

                        // Public: TV content streaming (HLS + direct stream + images)
                        .requestMatchers(HttpMethod.GET, "/serveiq/api/tv-content/stream/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/serveiq/api/tv-content/hls/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/images/**").permitAll()

                        // Public: App config images and dashboard (for TV/Feedback/Kiosk displays)
                        .requestMatchers(HttpMethod.GET, "/serveiq/api/app-config/images/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/serveiq/api/app-config/*/dashboard").permitAll()
                        .requestMatchers(HttpMethod.GET, "/serveiq/api/app-config/templates/*/active").permitAll()

                        // Walk-up kiosk
                        .requestMatchers(HttpMethod.POST, "/serveiq/api/tokens/generate").permitAll()
                        .requestMatchers(HttpMethod.POST, "/serveiq/api/feedback").permitAll()
                        .requestMatchers(HttpMethod.GET, "/serveiq/api/tv-display/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/serveiq/api/news/breaking-news").permitAll()
                        .requestMatchers(HttpMethod.GET, "/serveiq/api/news/images/**").permitAll()

                        // Admin only
                        .requestMatchers("/serveiq/api/auth/register").hasRole("ADMIN")
                        .requestMatchers("/serveiq/api/users/**").permitAll()
                        .requestMatchers("/api/admin/**", "/serveiq/api/admin/**").hasAnyRole("ADMIN","MANAGER")
                        .requestMatchers("/serveiq/api/reports/**").hasAnyRole("ADMIN", "MANAGER")
                        .requestMatchers("/serveiq/api/branches/**").hasAnyRole("ADMIN", "MANAGER")
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