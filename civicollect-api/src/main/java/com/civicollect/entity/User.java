package com.civicollect.entity;

import com.civicollect.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "users")
@Getter
@Setter
public class User extends BaseEntity {

    @Column(nullable = false, length = 120)
    private String fullName;

    @Column(unique = true, length = 150)
    private String email;

    @Column(unique = true, length = 20)
    private String phone;

    @Column(nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Enumerated(EnumType.STRING)
    private RequesterType requesterType;

    @ManyToOne(fetch = FetchType.LAZY)
    private Organization organization;

    /** Service municipal de rattachement (agents). */
    @ManyToOne(fetch = FetchType.LAZY)
    private MunicipalService municipalService;

    private boolean enabled = true;

    /** Élu habilité à modifier les pondérations et à ajuster le classement (RG4, RG5). */
    private boolean habilitated;

    /** Consentement explicite au traitement des données personnelles. */
    private Instant consentGivenAt;
}
