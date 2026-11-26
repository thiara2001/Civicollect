package com.civicollect.service.scoring;

import com.civicollect.domain.Criterion;
import com.civicollect.domain.Need;

/**
 * Pattern Strategy : une implémentation par critère. Ajouter un critère = ajouter un @Component,
 * sans modifier {@code ScoringService} (principe ouvert/fermé, évolutivité).
 */
public interface ScoringStrategy {

    boolean supports(Criterion criterion);

    /** Score normalisé dans [0, 1]. */
    double score(Need need, Criterion criterion, ScoringContext context);

    static double clamp01(double v) {
        if (Double.isNaN(v)) return 0.0;
        return Math.max(0.0, Math.min(1.0, v));
    }
}
