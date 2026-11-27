package com.civicollect.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "municipal_services")
@Getter
@Setter
public class MunicipalService extends BaseEntity implements Named {

    @Column(nullable = false, unique = true, length = 150)
    private String name;
}
