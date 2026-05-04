package com.arknow.auth.util;

public final class IdentifierValidator {
    private static final String PHONE_REGEX = "^1[3-9]\\d{9}$";
    private static final String EMAIL_REGEX = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$";

    private IdentifierValidator() {}

    public static boolean isValidPhone(String phone) {
        return phone != null && phone.trim().matches(PHONE_REGEX);
    }

    public static boolean isValidEmail(String email) {
        return email != null && email.trim().matches(EMAIL_REGEX);
    }
}
