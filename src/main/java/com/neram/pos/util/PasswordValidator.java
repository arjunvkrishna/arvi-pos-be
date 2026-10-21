package com.neram.pos.util;

import java.util.regex.Pattern;

public class PasswordValidator {

    private static final Pattern UPPERCASE = Pattern.compile("[A-Z]");
    private static final Pattern LOWERCASE = Pattern.compile("[a-z]");
    private static final Pattern DIGIT = Pattern.compile("[0-9]");
    private static final Pattern SPECIAL = Pattern.compile("[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?`~]");

    public static void validate(String password) {
        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters long");
        }
        if (!UPPERCASE.matcher(password).find()) {
            throw new IllegalArgumentException("Password must contain at least one uppercase letter (A-Z)");
        }
        if (!LOWERCASE.matcher(password).find()) {
            throw new IllegalArgumentException("Password must contain at least one lowercase letter (a-z)");
        }
        if (!DIGIT.matcher(password).find()) {
            throw new IllegalArgumentException("Password must contain at least one digit (0-9)");
        }
        if (!SPECIAL.matcher(password).find()) {
            throw new IllegalArgumentException("Password must contain at least one special character (!@#$%^&* etc.)");
        }
    }

    public static boolean isValid(String password) {
        if (password == null || password.length() < 8) return false;
        return UPPERCASE.matcher(password).find()
                && LOWERCASE.matcher(password).find()
                && DIGIT.matcher(password).find()
                && SPECIAL.matcher(password).find();
    }
}
