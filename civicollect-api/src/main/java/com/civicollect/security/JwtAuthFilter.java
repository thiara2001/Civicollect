package com.civicollect.security;

import com.civicollect.service.TokenService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Authentifie la requête à partir de l'en-tête « Authorization: Bearer <jwt> ». */
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final AppUserDetailsService userDetailsService;
    private final TokenService tokenService;

    public JwtAuthFilter(JwtService jwtService, AppUserDetailsService userDetailsService, TokenService tokenService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
        this.tokenService = tokenService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            try {
                Claims claims = jwtService.parseClaims(header.substring(7));
                if (!tokenService.isAccessTokenRevoked(claims.getId())) {
                    Long userId = Long.valueOf(claims.getSubject());
                    AuthenticatedUser user = userDetailsService.loadById(userId);
                    if (user.isEnabled()) {
                        var auth = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
                        auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(auth);
                    }
                }
            } catch (JwtException | IllegalArgumentException | UsernameNotFoundException ex) {
                // jeton invalide : la requête reste anonyme et sera rejetée (401) si la route l'exige
                logger.debug("JWT rejeté : " + ex.getMessage());
            }
        }
        chain.doFilter(request, response);
    }
}
