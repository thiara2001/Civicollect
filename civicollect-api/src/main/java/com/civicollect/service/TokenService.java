package com.civicollect.service;

import com.civicollect.domain.RefreshToken;
import com.civicollect.domain.RevokedToken;
import com.civicollect.domain.User;
import com.civicollect.exception.BusinessRuleException;
import com.civicollect.repository.RefreshTokenRepository;
import com.civicollect.repository.RevokedTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;

/**
 * Responsabilité unique (SRP) : cycle de vie des jetons de rafraîchissement et révocation des JWT d'accès.
 */
@Service
@RequiredArgsConstructor
public class TokenService {

    private static final long REFRESH_VALIDITY_DAYS = 7;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository refreshTokens;
    private final RevokedTokenRepository revokedTokens;

    @Transactional
    public String issueRefreshToken(User user) {
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String token = HexFormat.of().formatHex(raw);
        RefreshToken entity = new RefreshToken();
        entity.setUser(user);
        entity.setTokenHash(AuthService.sha256(token));
        entity.setExpiresAt(Instant.now().plus(REFRESH_VALIDITY_DAYS, ChronoUnit.DAYS));
        refreshTokens.save(entity);
        return token;
    }

    @Transactional
    public User consumeRefreshToken(String rawRefreshToken) {
        RefreshToken token = refreshTokens.findByTokenHash(AuthService.sha256(rawRefreshToken))
                .orElseThrow(() -> new BusinessRuleException("Refresh token invalide"));
        if (token.isRevoked() || token.getExpiresAt().isBefore(Instant.now())) {
            throw new BusinessRuleException("Refresh token expiré ou révoqué");
        }
        User user = token.getUser();
        if (!user.isEnabled()) {
            throw new BusinessRuleException("Compte utilisateur désactivé");
        }
        token.setRevoked(true);
        return user;
    }

    @Transactional
    public void revokeAccessToken(String tokenId, Instant expiresAt) {
        if (tokenId == null || tokenId.isBlank()) return;
        if (!revokedTokens.existsByTokenId(tokenId)) {
            RevokedToken revoked = new RevokedToken();
            revoked.setTokenId(tokenId);
            revoked.setExpiresAt(expiresAt != null ? expiresAt : Instant.now().plus(2, ChronoUnit.HOURS));
            revokedTokens.save(revoked);
        }
    }

    @Transactional
    public void revokeUserRefreshTokens(Long userId) {
        refreshTokens.findByUserIdAndRevokedFalse(userId).forEach(t -> t.setRevoked(true));
    }

    @Transactional
    public void revokeSingleRefreshToken(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) return;
        refreshTokens.findByTokenHash(AuthService.sha256(rawRefreshToken))
                .ifPresent(t -> t.setRevoked(true));
    }

    @Transactional(readOnly = true)
    public boolean isAccessTokenRevoked(String tokenId) {
        return tokenId != null && revokedTokens.existsByTokenId(tokenId);
    }
}
