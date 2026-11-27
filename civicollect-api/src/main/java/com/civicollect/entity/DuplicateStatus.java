package com.civicollect.domain;

/** État du cycle de vie d'une proposition de doublon entre deux contributions. */
public enum DuplicateStatus {
    POTENTIAL,
    CONFIRMED,
    IGNORED
}
