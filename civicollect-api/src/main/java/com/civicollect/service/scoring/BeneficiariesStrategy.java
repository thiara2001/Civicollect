package com.civicollect.service.scoring;

import com.civicollect.domain.Criterion;
import com.civicollect.domain.CriterionType;
import com.civicollect.domain.Need;
import org.springframework.stereotype.Component;

/** Nombre de personnes touchées, en échelle logarithmique (évite qu'un grand nombre écrase tout). */
@Component
public class BeneficiariesStrategy implements ScoringStrategy {

    @Override
    public boolean supports(Criterion c) {
        return c.getType() == CriterionType.AUTO && CriterionCodes.BENEFICIARIES.equals(c.getCode());
    }

    @Override
    public double score(Need need, Criterion c, ScoringContext ctx) {
        Integer b = need.getEstimatedBeneficiaries();
        if (b == null || b <= 0 || ctx.maxBeneficiaries() <= 0) return 0.0;
        return ScoringStrategy.clamp01(Math.log1p(b) / Math.log1p(ctx.maxBeneficiaries()));
    }
}
