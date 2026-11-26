package com.civicollect.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Quartier / zone géographique. La population (optionnelle) alimente le critère d'équité territoriale. */
@Entity
@Table(name = "zones")
@Getter
@Setter
public class Zone extends BaseEntity implements Named {

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    private Double latitude;
    private Double longitude;
    private Integer population;
}
