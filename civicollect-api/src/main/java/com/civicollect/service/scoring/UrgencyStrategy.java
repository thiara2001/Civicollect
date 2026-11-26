package com.civicollect.service.scoring;

import com.civicollect.domain.Criterion;
import com.civicollect.domain.CriterionType;
import com.civicollect.domain.Need;
import org.springframework.stereotype.Component;

/** Urgence perçue (FAIBLE 0,25 → CRITIQUE 1,0). */
@Component
public class UrgencyStrategy implements ScoringStrategy {

    @Override
    public boolean supports(Criterion c) {
        return c.getType() == CriterionType.AUTO && CriterionCodes.URGENCY.equals(c.getCode());
    }

    @Override
    public double score(Need need, Criterion c, ScoringContext ctx) {
        return need.getUrgency() == null ? 0.0 : need.getUrgency().getWeight();
    }
}
