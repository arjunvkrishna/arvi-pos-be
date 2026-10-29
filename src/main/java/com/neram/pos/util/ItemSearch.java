package com.neram.pos.util;

import java.util.Locale;

/**
 * Finding a dish by what the owner types. "biriyani" must find "Chicken Biryani": spellings of the same dish differ in
 * their vowels, so a name matches when it contains the typed text, or (for words of at least three consonants) when its
 * consonants contain the typed word's consonants.
 */
public final class ItemSearch {

    private ItemSearch() {}

    /** Lower case with only letters and digits: "Chicken Biryani (Full)" -> "chickenbiryanifull". */
    public static String normalize(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder();
        for (char c : s.toLowerCase(Locale.ROOT).toCharArray()) if (Character.isLetterOrDigit(c)) b.append(c);
        return b.toString();
    }

    /** The consonants of the normalised text with repeats collapsed: "biriyani" and "biryani" both become "bryn". */
    public static String skeleton(String s) {
        String n = normalize(s);
        StringBuilder b = new StringBuilder();
        for (char c : n.toCharArray()) {
            if ("aeiou".indexOf(c) >= 0) continue;
            if (b.length() > 0 && b.charAt(b.length() - 1) == c) continue;
            b.append(c);
        }
        return b.toString();
    }

    /** True when there is nothing typed, or the name fits the typed text (see the class comment). */
    public static boolean matches(String query, String name) {
        String q = normalize(query);
        if (q.isEmpty()) return true;
        String n = normalize(name);
        if (n.contains(q)) return true;
        String qs = skeleton(query);
        return qs.length() >= 3 && skeleton(name).contains(qs);
    }
}
