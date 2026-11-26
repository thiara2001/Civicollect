package com.civicollect.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** « Moi aussi » : un utilisateur ne soutient un besoin qu'une seule fois (RG7, contrainte d'unicité en base). */
@Entity
@Table(name = "supports", uniqueConstraints = @UniqueConstraint(name = "uk_support_user_need", columnNames = {"user_id", "need_id"}))
@Getter
@Setter
public class Support extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Need need;
}
