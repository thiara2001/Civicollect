package com.civicollect.util;

import java.text.Normalizer;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.Arrays;

public final class TextUtils {
    private TextUtils() {}

    private static final Pattern DIACRITICS = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
    private static final Set<String> STOPWORDS = Set.of(
            "les", "des", "une", "dans", "pour", "avec", "sur", "est", "sont", "pas", "que", "qui", "aux",
            "par", "plus", "tout", "tous", "nous", "vous", "ils", "elle", "elles", "mais", "donc", "leur",
            "leurs", "cette", "ces", "ses", "son", "sa", "notre", "nos", "ete", "etre", "avoir", "fait");

    /** Minuscules, sans accents. */
    public static String normalize(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s.toLowerCase(), Normalizer.Form.NFD);
        return DIACRITICS.matcher(n).replaceAll("");
    }

    /** Jetons significatifs d'un texte (sans accents, sans mots vides, longueur >= 3). */
    public static Set<String> tokens(String s) {
        return Arrays.stream(normalize(s).split("[^a-z0-9]+"))
                .filter(t -> t.length() >= 3 && !STOPWORDS.contains(t))
                .collect(Collectors.toSet());
    }

    public static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    /** Neutralise l'injection de formules dans les exports CSV ouverts avec un tableur. */
    public static String csvSafe(String s) {
        if (s == null) return "";
        if (!s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0) {
            return "'" + s;
        }
        return s;
    }
}
