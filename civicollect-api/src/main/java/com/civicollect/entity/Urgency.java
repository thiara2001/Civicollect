package com.civicollect.entity;

public enum Urgency {
    FAIBLE(0.25), MOYENNE(0.50), ELEVEE(0.75), CRITIQUE(1.00);

    private final double weight;

    Urgency(double weight) {
        this.weight = weight;
    }

    public double getWeight() {
        return weight;
    }
}
