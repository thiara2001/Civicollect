package com.civicollect.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NeedStatusTest {

    @Test
    void nominalLifecycleIsAllowed() {
        assertTrue(NeedStatus.BROUILLON.canMoveTo(NeedStatus.RECU));
        assertTrue(NeedStatus.RECU.canMoveTo(NeedStatus.EN_ANALYSE));
        assertTrue(NeedStatus.EN_ANALYSE.canMoveTo(NeedStatus.RETENU));
        assertTrue(NeedStatus.RETENU.canMoveTo(NeedStatus.PLANIFIE));
        assertTrue(NeedStatus.PLANIFIE.canMoveTo(NeedStatus.EN_COURS));
        assertTrue(NeedStatus.EN_COURS.canMoveTo(NeedStatus.REALISE));
    }

    @Test
    void shortcutsAndBacktrackingAreForbidden() {
        assertFalse(NeedStatus.RECU.canMoveTo(NeedStatus.RETENU));
        assertFalse(NeedStatus.EN_ANALYSE.canMoveTo(NeedStatus.RECU));
        assertFalse(NeedStatus.PLANIFIE.canMoveTo(NeedStatus.REALISE));
    }

    @Test
    void terminalStatesHaveNoTransition() {
        assertTrue(NeedStatus.REALISE.allowedTargets().isEmpty());
        assertTrue(NeedStatus.REJETE.allowedTargets().isEmpty());
    }

    @Test
    void rankingRequiresPriorValidation() {   // RG2
        assertFalse(NeedStatus.RECU.isRankable());
        assertFalse(NeedStatus.REJETE.isRankable());
        assertTrue(NeedStatus.EN_ANALYSE.isRankable());
    }

    @Test
    void onlyDecidedNeedsArePublic() {        // RF30
        assertFalse(NeedStatus.EN_ANALYSE.isPublic());
        assertTrue(NeedStatus.RETENU.isPublic());
        assertTrue(NeedStatus.REALISE.isPublic());
    }
}
