package com.neram.pos.util;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;

/**
 * Case-insensitive "contains" search for list filters, with the user's text matched literally (no LIKE wildcards).
 * The escape character is '!' everywhere (criteria queries and hand-written native SQL), so a pattern from here works in both.
 */
public final class LikeSearch {

    public static final char ESCAPE = '!';

    private LikeSearch() {
    }

    /** The LIKE pattern for the text, or null when there is nothing to search for. */
    public static String pattern(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String escaped = text.trim().toLowerCase().replace("!", "!!").replace("%", "!%").replace("_", "!_");
        return "%" + escaped + "%";
    }

    /** Same pattern for native queries that treat an empty string as "no search" (a NULL parameter has no type in SQL). */
    public static String nativePattern(String text) {
        String p = pattern(text);
        return p == null ? "" : p;
    }

    /** lower(coalesce(column, '')) LIKE pattern, so a missing value simply does not match. */
    public static Predicate contains(CriteriaBuilder cb, Expression<String> column, String pattern) {
        return cb.like(cb.lower(cb.coalesce(column, "")), pattern, ESCAPE);
    }
}
