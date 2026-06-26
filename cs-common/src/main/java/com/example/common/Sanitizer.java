package com.example.common;

/**
 * 安全工具：XSS 净化 + 字段长度限制 + 路径穿越防护
 *
 * <p>所有进入系统的文本字段都应先过 {@link #text(String, int)}
 */
public final class Sanitizer {
    private Sanitizer() {}

    /** 文本字段净化（去除 HTML 标签、控制字符，限制长度） */
    public static String text(String input, int maxLen) {
        if (input == null) return null;
        String s = input;
        // 1) 去除控制字符（除 \n \r \t）
        s = s.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "");
        // 2) 去除 HTML/脚本标签
        s = s.replaceAll("<[^>]+>", "");
        // 3) 处理常见 XSS 模式
        s = s.replaceAll("(?i)javascript:", "")
             .replaceAll("(?i)vbscript:", "")
             .replaceAll("(?i)onerror=", "")
             .replaceAll("(?i)onload=", "")
             .replaceAll("(?i)onclick=", "")
             .replaceAll("(?i)onmouseover=", "");
        // 4) 截断
        if (s.length() > maxLen) s = s.substring(0, maxLen);
        return s.trim();
    }

    /** 文件名校验：仅允许字母数字 + . _ - */
    public static String safeFileName(String name) {
        if (name == null) return "file";
        // 去除路径部分（防 ../ 攻击）
        String base = name;
        int slash = Math.max(base.lastIndexOf('/'), base.lastIndexOf('\\'));
        if (slash >= 0) base = base.substring(slash + 1);
        // 仅保留合法字符
        base = base.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (base.length() > 200) base = base.substring(0, 200);
        return base.isEmpty() ? "file" : base;
    }

    /** emoji 净化：仅保留合法字符（防 SQL/命令注入） */
    public static String emoji(String e) {
        if (e == null) return null;
        // 限制：emoji 通常是 1-8 个字符的 surrogate pair
        if (e.length() > 16) return null;
        // 仅允许 BMP 字符 + emoji 区
        if (!e.matches("[\\p{So}\\p{Sk}\\p{Sm}\\p{Sc}\\u2700-\\u27BF]+")) return null;
        return e;
    }
}