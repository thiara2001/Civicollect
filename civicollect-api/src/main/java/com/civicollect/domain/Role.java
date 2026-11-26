package com.civicollect.domain;

public enum Role {
    HABITANT, ASSOCIATION, AGENT, ELU, ADMIN;

    public boolean isStaff() {
        return this == AGENT || this == ELU || this == ADMIN;
    }
}
