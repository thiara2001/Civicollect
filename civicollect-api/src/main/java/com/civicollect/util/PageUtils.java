package com.civicollect.util;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;

public final class PageUtils {
    private PageUtils() {}

    public static final Set<String> NEED_SORTS = Set.of(
            "id", "createdAt", "submittedAt", "title", "urgency", "status", "priorityScore");

    /** Borne la taille de page et n'accepte que des propriétés de tri connues (évite les erreurs 500 / abus). */
    public static Pageable sanitize(Pageable p, Set<String> allowedSorts, Sort defaultSort) {
        int size = Math.max(1, Math.min(p.getPageSize(), 100));
        Sort sort = defaultSort;
        if (p.getSort().isSorted() && p.getSort().stream().allMatch(o -> allowedSorts.contains(o.getProperty()))) {
            sort = p.getSort();
        }
        return PageRequest.of(p.getPageNumber(), size, sort);
    }
}
