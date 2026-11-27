package com.civicollect.service.duplicate;

import com.civicollect.domain.Need;

/**
 * Pattern Strategy (SOLID - Ouvert/Fermé) : algorithme de calcul de similarité entre deux contributions.
 * Permet d'introduire de nouvelles heuristiques (Levenshtein, géolocalisation, embeddings) sans modifier le service.
 */
public interface SimilarityStrategy {
    /** Score de similarité normalisé dans [0, 1]. */
    double compute(Need first, Need second);
}
