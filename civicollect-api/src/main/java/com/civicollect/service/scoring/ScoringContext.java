package com.civicollect.service.scoring;

import com.civicollect.domain.Need;

import java.util.Map;
import java.util.Optional;

/**
 * Données agrégées sur l'ensemble des besoins classés, nécessaires à la normalisation des critères
 * (maxima, taux par zone, soutiens...). Construit une fois par calcul de classement.
 */
public class ScoringContext {

    private final Map<Long, Integer> requestCounts;
    private final Map<Long, Map<String, Double>> manualValues;
    private final double maxBeneficiaries;
    private final double maxCost;
    private final Map<Long, Double> zoneRates;
    private final double maxZoneRate;
    private final int supportCap;

    public ScoringContext(Map<Long, Integer> requestCounts, Map<Long, Map<String, Double>> manualValues,
                          double maxBeneficiaries, double maxCost, Map<Long, Double> zoneRates,
                          double maxZoneRate, int supportCap) {
        this.requestCounts = requestCounts;
        this.manualValues = manualValues;
        this.maxBeneficiaries = maxBeneficiaries;
        this.maxCost = maxCost;
        this.zoneRates = zoneRates;
        this.maxZoneRate = maxZoneRate;
        this.supportCap = Math.max(1, supportCap);
    }

    /** Nombre de demandes = demandeur d'origine + soutiens (+ demandes regroupées). */
    public int requestCount(Need n) {
        return requestCounts.getOrDefault(n.getId(), 1);
    }

    public Optional<Double> manualValue(Need n, String criterionCode) {
        return Optional.ofNullable(manualValues.getOrDefault(n.getId(), Map.of()).get(criterionCode));
    }

    /** Dépôts par habitant (ou par zone si population inconnue) ; null si le besoin n'a pas de zone. */
    public Double zoneRate(Need n) {
        if (n.getZone() == null) return null;
        return zoneRates.getOrDefault(n.getZone().getId(), 0.0);
    }

    public double maxBeneficiaries() { return maxBeneficiaries; }
    public double maxCost() { return maxCost; }
    public double maxZoneRate() { return maxZoneRate; }
    public int supportCap() { return supportCap; }
}
