package com.civicollect.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DuplicateDetectionTest {

    @Test
    void similarTextsScoreHigh() {
        double s = DuplicateDetectionService.jaccard(
                "Nid de poule dangereux avenue Léopold Sédar Senghor",
                "Dangereux nid de poule sur l'avenue Léopold Sédar Senghor");
        assertTrue(s > 0.6, "score = " + s);
    }

    @Test
    void unrelatedTextsScoreLow() {
        double s = DuplicateDetectionService.jaccard(
                "Manque de médicaments au poste de santé",
                "Éclairage public en panne près du marché");
        assertTrue(s < 0.2, "score = " + s);
    }

    @Test
    void accentsAndCaseAreIgnored() {
        assertEquals(1.0, DuplicateDetectionService.jaccard("ÉCOLE délabrée", "ecole delabree"), 1e-9);
    }

    @Test
    void emptyTextGivesZero() {
        assertEquals(0.0, DuplicateDetectionService.jaccard("", "quelque chose"), 1e-9);
    }
}
