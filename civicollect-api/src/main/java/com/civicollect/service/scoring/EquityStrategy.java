package com.civicollect.service.scoring;

import com.civicollect.domain.Criterion;
import com.civicollect.domain.CriterionType;
import com.civicollect.domain.Need;
import org.springframework.stereotype.Component;

/**
 * Équité territoriale : compense les zones sous-représentées (peu de dépôts par habitant).
 * La zone la plus active obtient 0, une zone sans dépôt obtient 1.
 */
@Component
public class EquityStrategy implements ScoringStrategy {

    @Override
    public boolean supports(Criterion c) {
        return c.getType() == CriterionType.AUTO && CriterionCodes.EQUITY.equals(c.getCode());
    }

    @Override
    public double score(Need need, Criterion c, ScoringContext ctx) {
        Double rate = ctx.zoneRate(need);
        if (rate == null || ctx.maxZoneRate() <= 0) return 0.5;    // zone inconnue : valeur neutre
        return ScoringStrategy.clamp01(1.0 - rate / ctx.maxZoneRate());
    }
}
