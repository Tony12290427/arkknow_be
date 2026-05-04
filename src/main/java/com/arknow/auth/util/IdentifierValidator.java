package com.arknow.auth.util;

/**
 * Static utility for validating phone numbers and email addresses.
 * <p>
 * These are format-level checks only — they do not verify deliverability.
 * Phone numbers must start with 1 and be exactly 11 digits (Chinese mobile format).
 * Emails are checked against a basic pattern covering common formats.
 */
public final class IdentifierValidator {
    /** Chinese mobile: starts with 1, followed by 3-9, then 9 digits. */
    private static final String PHONE_REGEX = "^1[3-9]\\d{9}$";
    /** Basic email pattern: local-part@domain.tld */
    private static final String EMAIL_REGEX = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$";

    private IdentifierValidator() {}

    public static boolean isValidPhone(String phone) {
        return phone != null && phone.trim().matches(PHONE_REGEX);
    }

    public static boolean isValidEmail(String email) {
        return email != null && email.trim().matches(EMAIL_REGEX);
    }
}
