package com.civicollect.service.scoring;

import com.civicollect.domain.Criterion;
import com.civicollect.domain.CriterionType;
import com.civicollect.domain.Need;
import org.springframework.stereotype.Component;

/** Nombre de demandes et de soutiens, plafonné pour limiter l'avantage des groupes les mieux organisés. */
@Component
public class SupportsStrategy implements ScoringStrategy {

    @Override
    public boolean supports(Criterion c) {
        return c.getType() == CriterionType.AUTO && CriterionCodes.SUPPORTS.equals(c.getCode());
    }

    @Override
    public double score(Need need, Criterion c, ScoringContext ctx) {
        int capped = Math.min(ctx.requestCount(need), ctx.supportCap());
        return ScoringStrategy.clamp01((double) capped / ctx.supportCap());
    }
}
