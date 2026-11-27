package com.civicollect.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** Jeton de rafraîchissement JWT persisté (stocké sous forme hachée SHA-256, révocable et à rotation). */
@Entity
@Table(name = "refresh_tokens", indexes = @Index(name = "idx_refresh_user", columnList = "user_id"))
@Getter
@Setter
public class RefreshToken extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private User user;

    @Column(nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(nullable = false)
    private Instant expiresAt;

    private boolean revoked;
}
