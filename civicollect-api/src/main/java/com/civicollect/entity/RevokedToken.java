package com.civicollect.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** Identifiant JWT révoqué lors d'une déconnexion explicite (logout). */
@Entity
@Table(name = "revoked_tokens", indexes = @Index(name = "idx_revoked_expires", columnList = "expiresAt"))
@Getter
@Setter
public class RevokedToken extends BaseEntity {

    @Column(nullable = false, unique = true, length = 64)
    private String tokenId;

    @Column(nullable = false)
    private Instant expiresAt;
}
