package com.example.im.service;

import org.springframework.stereotype.Service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 富文本 / Markdown 服务 — 服务端基础 Markdown 渲染
 *
 * <p>支持语法：标题/粗体/斜体/链接/列表/代码块/行内代码/水平线
 *
 * <p>前端可用 marked.js + highlight.js 进一步渲染高亮。
 */
@Service
public class RichMessageService {

    private static final Pattern H1 = Pattern.compile("(?m)^# (.+)$");
    private static final Pattern H2 = Pattern.compile("(?m)^## (.+)$");
    private static final Pattern H3 = Pattern.compile("(?m)^### (.+)$");
    private static final Pattern BOLD = Pattern.compile("\\*\\*(.+?)\\*\\*");
    private static final Pattern ITALIC = Pattern.compile("\\*(.+?)\\*");
    private static final Pattern LINK = Pattern.compile("\\[(.+?)]\\((.+?)\\)");
    private static final Pattern CODE_BLOCK = Pattern.compile("```([\\s\\S]+?)```");
    private static final Pattern INLINE_CODE = Pattern.compile("`([^`]+)`");
    private static final Pattern HR = Pattern.compile("(?m)^---+$");
    private static final Pattern UL_ITEM = Pattern.compile("(?m)^[*\\-] (.+)$");
    private static final Pattern OL_ITEM = Pattern.compile("(?m)^\\d+\\. (.+)$");

    /**
     * 把 Markdown 转 HTML（简单版）
     */
    public String render(String markdown) {
        if (markdown == null || markdown.isBlank()) return "";
        String html = escape(markdown);

        // 代码块（先处理，避免内部 * 被替换）
        Matcher codeBlock = CODE_BLOCK.matcher(html);
        StringBuffer sb = new StringBuffer();
        while (codeBlock.find()) {
            String content = codeBlock.group(1).trim();
            codeBlock.appendReplacement(sb,
                Matcher.quoteReplacement("<pre><code>" + content + "</code></pre>"));
        }
        codeBlock.appendTail(sb);
        html = sb.toString();

        html = H1.matcher(html).replaceAll("<h1>$1</h1>");
        html = H2.matcher(html).replaceAll("<h2>$1</h2>");
        html = H3.matcher(html).replaceAll("<h3>$1</h3>");
        html = BOLD.matcher(html).replaceAll("<strong>$1</strong>");
        html = ITALIC.matcher(html).replaceAll("<em>$1</em>");
        html = LINK.matcher(html).replaceAll("<a href=\"$2\" target=\"_blank\">$1</a>");
        html = INLINE_CODE.matcher(html).replaceAll("<code>$1</code>");
        html = HR.matcher(html).replaceAll("<hr/>");
        html = UL_ITEM.matcher(html).replaceAll("<li>$1</li>");
        html = OL_ITEM.matcher(html).replaceAll("<li>$1</li>");

        // 包裹 li 在 ul/ol（简化：全部 ul）
        html = html.replaceAll("(<li>[^<]+</li>(?:\\s*<li>[^<]+</li>)*)", "<ul>$1</ul>");

        // 段落（双换行分隔）
        html = html.replaceAll("\\n\\n", "</p><p>");
        html = "<p>" + html + "</p>";
        html = html.replaceAll("<p>(<h\\d>.*?</h\\d>)</p>", "$1");
        html = html.replaceAll("<p>(<ul>.*?</ul>)</p>", "$1");
        html = html.replaceAll("<p>(<pre>.*?</pre>)</p>", "$1");
        html = html.replaceAll("<p>(<hr/>)</p>", "$1");

        return html;
    }

    /**
     * 提取纯文本（用于搜索 / 预览）
     */
    public String plainText(String markdown) {
        if (markdown == null) return "";
        return markdown
            .replaceAll("```[\\s\\S]+?```", "[代码]")
            .replaceAll("[#*_`>]", "")
            .replaceAll("\\[(.+?)]\\(.+?\\)", "$1")
            .trim();
    }

    private String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}