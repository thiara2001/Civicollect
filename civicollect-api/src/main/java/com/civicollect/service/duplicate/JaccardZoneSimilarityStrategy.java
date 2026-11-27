package com.civicollect.service.duplicate;

import com.civicollect.domain.Need;
import com.civicollect.util.TextUtils;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/** Stratégie par défaut : similarité de Jaccard sur les jetons (titre + description) avec bonus de même quartier. */
@Component
public class JaccardZoneSimilarityStrategy implements SimilarityStrategy {

    private static final double SAME_ZONE_BONUS = 0.10;

    @Override
    public double compute(Need first, Need second) {
        double base = jaccard(first.getTitle() + " " + first.getDescription(),
                second.getTitle() + " " + second.getDescription());
        boolean sameZone = first.getZone() != null && second.getZone() != null
                && first.getZone().getId().equals(second.getZone().getId());
        return Math.min(1.0, base + (sameZone ? SAME_ZONE_BONUS : 0.0));
    }

    public static double jaccard(String a, String b) {
        Set<String> ta = TextUtils.tokens(a);
        Set<String> tb = TextUtils.tokens(b);
        if (ta.isEmpty() || tb.isEmpty()) return 0.0;
        Set<String> inter = new HashSet<>(ta);
        inter.retainAll(tb);
        Set<String> union = new HashSet<>(ta);
        union.addAll(tb);
        return (double) inter.size() / union.size();
    }
}
