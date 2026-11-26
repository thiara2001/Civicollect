package com.civicollect.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limitation de débit (anti-spam / force brute) par adresse IP sur les routes sensibles.
 * Implémentation en mémoire (fenêtre glissante d'une heure) ; à remplacer par Redis en multi-instances.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> LIMITED_PATHS = Set.of(
            "/api/public/needs/anonymous", "/api/auth/login", "/api/auth/register", "/api/auth/forgot-password");

    private final int maxPerHour;
    private final ConcurrentHashMap<String, Deque<Instant>> hits = new ConcurrentHashMap<>();

    public RateLimitFilter(int maxPerHour) {
        this.maxPerHour = maxPerHour;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || !LIMITED_PATHS.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String key = request.getRemoteAddr() + "|" + request.getRequestURI();
        Deque<Instant> window = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        boolean allowed;
        synchronized (window) {
            Instant limit = Instant.now().minus(Duration.ofHours(1));
            while (!window.isEmpty() && window.peekFirst().isBefore(limit)) {
                window.pollFirst();
            }
            allowed = window.size() < maxPerHour;
            if (allowed) {
                window.addLast(Instant.now());
            }
        }
        if (!allowed) {
            SecurityErrorWriter.write(response, 429, "Trop de requêtes, réessayez plus tard");
            return;
        }
        chain.doFilter(request, response);
    }
}
