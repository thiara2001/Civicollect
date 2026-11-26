package com.civicollect.service.scoring;

/** Codes des critères automatiques fournis par défaut. */
public final class CriterionCodes {
    private CriterionCodes() {}

    public static final String SUPPORTS = "SUPPORTS";
    public static final String BENEFICIARIES = "BENEFICIARIES";
    public static final String URGENCY = "URGENCY";
    public static final String COST_FEASIBILITY = "COST_FEASIBILITY";
    public static final String EQUITY = "EQUITY";
    // Critères manuels (valeur 0-10 saisie par l'agent) : GRAVITY, SOCIAL_IMPACT, HEALTH_IMPACT
    public static final String GRAVITY = "GRAVITY";
    public static final String SOCIAL_IMPACT = "SOCIAL_IMPACT";
    public static final String HEALTH_IMPACT = "HEALTH_IMPACT";
}
