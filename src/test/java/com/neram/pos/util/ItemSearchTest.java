package com.neram.pos.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ItemSearchTest {

    @Test
    @DisplayName("different spellings of the same dish find each other")
    void spellingsOfBiryani() {
        for (String typed : new String[]{"biriyani", "biryani", "briyani", "Biriyani", "BIRYANI"}) {
            assertTrue(ItemSearch.matches(typed, "Chicken Biryani"), typed);
            assertTrue(ItemSearch.matches(typed, "Mutton Biriyani (Full)"), typed);
            assertTrue(ItemSearch.matches(typed, "Veg Briyani"), typed);
        }
    }

    @Test
    @DisplayName("something else is not found")
    void unrelatedDishesAreNotFound() {
        assertFalse(ItemSearch.matches("biriyani", "Butter Naan"));
        assertFalse(ItemSearch.matches("biriyani", "Paneer Tikka"));
        assertFalse(ItemSearch.matches("burger", "Chicken Biryani"));
    }

    @Test
    @DisplayName("an exact fragment always matches, whatever its length")
    void shortFragmentsMatchDirectly() {
        assertTrue(ItemSearch.matches("tea", "Masala Tea"));
        assertTrue(ItemSearch.matches("dosa", "Masala Dosa"));
        assertFalse(ItemSearch.matches("tea", "Cold Coffee"));
    }

    @Test
    @DisplayName("nothing typed matches everything; punctuation and case do not matter")
    void emptyAndPunctuation() {
        assertTrue(ItemSearch.matches("", "Anything"));
        assertTrue(ItemSearch.matches(null, "Anything"));
        assertTrue(ItemSearch.matches("  ", "Anything"));
        assertTrue(ItemSearch.matches("chicken-biryani", "CHICKEN  BIRYANI"));
    }

    @Test
    @DisplayName("a very short consonant skeleton is not used for fuzzy matching (it would match too much)")
    void shortSkeletonsAreNotFuzzy() {
        assertFalse(ItemSearch.matches("idli", "Dal Tadka"));   // skeleton "dl" is only two letters
        assertEquals("bryn", ItemSearch.skeleton("biriyani"));
        assertEquals("bryn", ItemSearch.skeleton("Biryani"));
    }
}
