package com.civicollect.service;

import com.civicollect.domain.*;
import com.civicollect.service.scoring.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ScoringStrategiesTest {

    private static Need need(long id) {
        Need n = new Need();
        n.setId(id);
        return n;
    }

    private static Criterion criterion(String code, CriterionType type) {
        Criterion c = new Criterion();
        c.setCode(code);
        c.setType(type);
        c.setWeight(10);
        return c;
    }

    private static ScoringContext ctx(Map<Long, Integer> requests, double maxB, double maxCost,
                                      Map<Long, Double> zoneRates, double maxRate, int cap) {
        return new ScoringContext(requests, Map.of(), maxB, maxCost, zoneRates, maxRate, cap);
    }

    @Test
    void supportsAreCappedToLimitOrganisedGroups() {
        Need n = need(1);
        ScoringContext c = ctx(Map.of(1L, 500), 0, 0, Map.of(), 0, 50);
        assertEquals(1.0, new SupportsStrategy().score(n, criterion(CriterionCodes.SUPPORTS, CriterionType.AUTO), c), 1e-9);
    }

    @Test
    void supportsScaleLinearlyBelowTheCap() {
        Need n = need(1);
        ScoringContext c = ctx(Map.of(1L, 10), 0, 0, Map.of(), 0, 50);
        assertEquals(0.2, new SupportsStrategy().score(n, criterion(CriterionCodes.SUPPORTS, CriterionType.AUTO), c), 1e-9);
    }

    @Test
    void urgencyUsesEnumWeight() {
        Need n = need(1);
        n.setUrgency(Urgency.CRITIQUE);
        assertEquals(1.0, new UrgencyStrategy().score(n, criterion(CriterionCodes.URGENCY, CriterionType.AUTO),
                ctx(Map.of(), 0, 0, Map.of(), 0, 50)), 1e-9);
    }

    @Test
    void beneficiariesAreNormalisedAgainstTheMaximum() {
        Need big = need(1);
        big.setEstimatedBeneficiaries(10_000);
        Need small = need(2);
        small.setEstimatedBeneficiaries(100);
        ScoringContext c = ctx(Map.of(), 10_000, 0, Map.of(), 0, 50);
        BeneficiariesStrategy s = new BeneficiariesStrategy();
        Criterion crit = criterion(CriterionCodes.BENEFICIARIES, CriterionType.AUTO);
        assertEquals(1.0, s.score(big, crit, c), 1e-9);
        assertTrue(s.score(small, crit, c) < 1.0 && s.score(small, crit, c) > 0.0);
    }

    @Test
    void cheaperNeedsAreMoreFeasible() {
        Need cheap = need(1);
        cheap.setEstimatedCost(new BigDecimal("100000"));
        Need pricey = need(2);
        pricey.setEstimatedCost(new BigDecimal("50000000"));
        ScoringContext c = ctx(Map.of(), 0, 50_000_000, Map.of(), 0, 50);
        CostFeasibilityStrategy s = new CostFeasibilityStrategy();
        Criterion crit = criterion(CriterionCodes.COST_FEASIBILITY, CriterionType.AUTO);
        assertTrue(s.score(cheap, crit, c) > s.score(pricey, crit, c));
        assertEquals(0.0, s.score(pricey, crit, c), 1e-9);
    }

    @Test
    void unknownCostIsNeutral() {
        assertEquals(0.5, new CostFeasibilityStrategy().score(need(1),
                criterion(CriterionCodes.COST_FEASIBILITY, CriterionType.AUTO), ctx(Map.of(), 0, 1000, Map.of(), 0, 50)), 1e-9);
    }

    @Test
    void underRepresentedZonesGetAnEquityBonus() {
        Zone busy = new Zone();
        busy.setId(1L);
        Zone quiet = new Zone();
        quiet.setId(2L);
        Need a = need(1);
        a.setZone(busy);
        Need b = need(2);
        b.setZone(quiet);
        ScoringContext c = ctx(Map.of(), 0, 0, Map.of(1L, 10.0, 2L, 1.0), 10.0, 50);
        EquityStrategy s = new EquityStrategy();
        Criterion crit = criterion(CriterionCodes.EQUITY, CriterionType.AUTO);
        assertEquals(0.0, s.score(a, crit, c), 1e-9);
        assertEquals(0.9, s.score(b, crit, c), 1e-9);
    }

    @Test
    void manualEvaluationIsScaledFromZeroToTen() {
        Need n = need(1);
        ScoringContext c = new ScoringContext(Map.of(), Map.of(1L, Map.of("GRAVITY", 8.0)), 0, 0, Map.of(), 0, 50);
        Criterion crit = criterion("GRAVITY", CriterionType.MANUAL);
        ManualEvaluationStrategy s = new ManualEvaluationStrategy();
        assertTrue(s.supports(crit));
        assertEquals(0.8, s.score(n, crit, c), 1e-9);
        assertEquals(0.0, s.score(need(2), crit, c), 1e-9);   // pas encore évalué
    }

    @Test
    void autoStrategiesDoNotClaimManualCriteria() {
        Criterion manualUrgency = criterion(CriterionCodes.URGENCY, CriterionType.MANUAL);
        assertFalse(new UrgencyStrategy().supports(manualUrgency));
    }
}
