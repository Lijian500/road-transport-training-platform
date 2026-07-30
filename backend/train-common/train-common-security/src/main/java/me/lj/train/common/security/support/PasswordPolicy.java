package me.lj.train.common.security.support;

import java.util.regex.Pattern;

/**
 * 统一密码强度规则。
 */
public final class PasswordPolicy {

    private static final Pattern LETTER = Pattern.compile("[A-Za-z]");
    private static final Pattern DIGIT = Pattern.compile("\\d");

    private PasswordPolicy() {
    }

    public static boolean isValid(String password) {
        return password != null
                && password.length() >= 8
                && password.length() <= 64
                && LETTER.matcher(password).find()
                && DIGIT.matcher(password).find();
    }
}
