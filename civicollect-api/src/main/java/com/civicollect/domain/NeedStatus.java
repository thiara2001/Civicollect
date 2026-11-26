package com.civicollect.domain;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Cycle de vie d'un besoin (RF28). Les transitions autorisées sont portées par l'énumération
 * elle-même (pattern State simplifié) : aucune règle de transition n'est dispersée dans les services.
 */
public enum NeedStatus {
    BROUILLON("Brouillon"),
    RECU("Reçu"),
    EN_ANALYSE("En analyse"),
    RETENU("Retenu"),
    PLANIFIE("Planifié"),
    EN_COURS("En cours"),
    REALISE("Réalisé"),
    REJETE("Rejeté");

    private static final Map<NeedStatus, Set<NeedStatus>> TRANSITIONS = new EnumMap<>(NeedStatus.class);

    static {
        TRANSITIONS.put(BROUILLON, EnumSet.of(RECU));
        TRANSITIONS.put(RECU, EnumSet.of(EN_ANALYSE, REJETE));
        TRANSITIONS.put(EN_ANALYSE, EnumSet.of(RETENU, REJETE));
        TRANSITIONS.put(RETENU, EnumSet.of(PLANIFIE, REJETE));
        TRANSITIONS.put(PLANIFIE, EnumSet.of(EN_COURS));
        TRANSITIONS.put(EN_COURS, EnumSet.of(REALISE));
        TRANSITIONS.put(REALISE, EnumSet.noneOf(NeedStatus.class));
        TRANSITIONS.put(REJETE, EnumSet.noneOf(NeedStatus.class));
    }

    private final String label;

    NeedStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public boolean canMoveTo(NeedStatus target) {
        return TRANSITIONS.get(this).contains(target);
    }

    public Set<NeedStatus> allowedTargets() {
        Set<NeedStatus> copy = EnumSet.noneOf(NeedStatus.class);
        copy.addAll(TRANSITIONS.get(this));
        return copy;
    }

    /** Visible sur la page publique de transparence (RF30). */
    public boolean isPublic() {
        return this == RETENU || this == PLANIFIE || this == EN_COURS || this == REALISE;
    }

    /** Un besoin n'est priorisable qu'après validation par un agent (RG2). */
    public boolean isRankable() {
        return this == EN_ANALYSE || this == RETENU || this == PLANIFIE || this == EN_COURS;
    }

    /** Validé par un agent : visible des citoyens, soutenable, commentable. */
    public boolean isValidated() {
        return this != BROUILLON && this != RECU && this != REJETE;
    }

    public static Set<NeedStatus> rankable() {
        Set<NeedStatus> s = EnumSet.noneOf(NeedStatus.class);
        for (NeedStatus st : values()) if (st.isRankable()) s.add(st);
        return s;
    }

    public static Set<NeedStatus> validated() {
        Set<NeedStatus> s = EnumSet.noneOf(NeedStatus.class);
        for (NeedStatus st : values()) if (st.isValidated()) s.add(st);
        return s;
    }

    public static Set<NeedStatus> publicStatuses() {
        Set<NeedStatus> s = EnumSet.noneOf(NeedStatus.class);
        for (NeedStatus st : values()) if (st.isPublic()) s.add(st);
        return s;
    }
}
