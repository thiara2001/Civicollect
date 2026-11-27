package com.civicollect.security;

import com.civicollect.config.AppProperties;
import com.civicollect.service.TokenService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
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
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtService jwtService,
                                           AppUserDetailsService userDetailsService, TokenService tokenService,
                                           AppProperties props) throws Exception {
        // Les filtres sont instanciés ici (et non exposés comme beans) pour éviter leur double enregistrement servlet.
        JwtAuthFilter jwtFilter = new JwtAuthFilter(jwtService, userDetailsService, tokenService);
        RateLimitFilter rateLimitFilter = new RateLimitFilter(props.rateLimit().perHour());

        http
                // API stateless authentifiée par en-tête Authorization (pas de cookie) : non exposée au CSRF classique.
                .csrf(AbstractHttpConfigurer::disable)
                .cors(c -> {})
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(h -> h
                        .contentTypeOptions(c -> {})
                        .frameOptions(f -> f.deny())
                        .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000)))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/api/auth/register", "/api/auth/login", "/api/auth/refresh",
                                "/api/auth/forgot-password", "/api/auth/reset-password").permitAll()
                        .requestMatchers("/api/public/**", "/api/reference/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/categories/**", "/api/quartiers/**",
                                "/api/contributions/publiques").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) ->
                                SecurityErrorWriter.write(res, 401, "Authentification requise"))
                        .accessDeniedHandler((req, res, ex) ->
                                SecurityErrorWriter.write(res, 403, "Accès refusé : droits insuffisants")))
                .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(AppProperties props) {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(props.cors().allowedOrigins());
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        cfg.setExposedHeaders(List.of("Content-Disposition"));
        cfg.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cfg);
        return source;
    }
}
