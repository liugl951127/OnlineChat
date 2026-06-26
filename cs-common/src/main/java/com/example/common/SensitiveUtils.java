package com.example.common;

/**
 * 数据脱敏工具（合规：身份证/手机号/银行卡）
 */
public final class SensitiveUtils {
    private SensitiveUtils() {}

    public static String maskMobile(String s) {
        if (s == null || s.length() < 7) return "***";
        return s.substring(0, 3) + "****" + s.substring(s.length() - 4);
    }

    public static String maskIdCard(String s) {
        if (s == null || s.length() < 8) return "***";
        return s.substring(0, 4) + "**********" + s.substring(s.length() - 4);
    }

    public static String maskBankCard(String s) {
        if (s == null || s.length() < 8) return "***";
        return s.substring(0, 4) + "******" + s.substring(s.length() - 4);
    }

    public static String maskName(String s) {
        if (s == null || s.isEmpty()) return "";
        if (s.length() == 1) return s;
        if (s.length() == 2) return s.charAt(0) + "*";
        return s.charAt(0) + "*".repeat(s.length() - 2) + s.charAt(s.length() - 1);
    }
}