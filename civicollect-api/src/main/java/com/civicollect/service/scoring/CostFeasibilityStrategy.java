package com.civicollect.service.scoring;

import com.civicollect.domain.Criterion;
import com.civicollect.domain.CriterionType;
import com.civicollect.domain.Need;
import org.springframework.stereotype.Component;

/** Coût estimé et faisabilité : plus le coût est faible relativement aux autres besoins, meilleur est le score. */
@Component
public class CostFeasibilityStrategy implements ScoringStrategy {

    @Override
    public boolean supports(Criterion c) {
        return c.getType() == CriterionType.AUTO && CriterionCodes.COST_FEASIBILITY.equals(c.getCode());
    }

    @Override
    public double score(Need need, Criterion c, ScoringContext ctx) {
        if (need.getEstimatedCost() == null) return 0.5;           // coût inconnu : valeur neutre
        if (ctx.maxCost() <= 0) return 1.0;
        double cost = need.getEstimatedCost().doubleValue();
        return ScoringStrategy.clamp01(1.0 - Math.log1p(cost) / Math.log1p(ctx.maxCost()));
    }
}
