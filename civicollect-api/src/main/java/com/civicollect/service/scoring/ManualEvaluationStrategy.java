package com.civicollect.service.scoring;

import com.civicollect.domain.Criterion;
import com.civicollect.domain.CriterionType;
import com.civicollect.domain.Need;
import org.springframework.stereotype.Component;

/** Critères qualitatifs (gravité, impact social, impact sanitaire...) évalués de 0 à 10 par un agent. */
@Component
public class ManualEvaluationStrategy implements ScoringStrategy {

    @Override
    public boolean supports(Criterion c) {
        return c.getType() == CriterionType.MANUAL;
    }

    @Override
    public double score(Need need, Criterion c, ScoringContext ctx) {
        return ctx.manualValue(need, c.getCode()).map(v -> ScoringStrategy.clamp01(v / 10.0)).orElse(0.0);
    }
}
