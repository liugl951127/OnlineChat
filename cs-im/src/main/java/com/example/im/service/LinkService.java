package com.example.im.service;

import com.example.common.ApiException;
import com.example.im.domain.ChatLink;
import com.example.im.repo.ChatLinkMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Set;

/**
 * v2.3.0 链接推送服务 - 坐席推链接给客户
 *
 * <p>防 SSRF:
 * <ul>
 *   <li>白名单: 必须是配置的域名 (cs.link.allowed-domains)</li>
 *   <li>黑名单: localhost / 127.* / 192.168.* / 10.* / *.gov</li>
 * </ul>
 *
 * <p>防滥用:
 * <ul>
 *   <li>shortToken 一次性, 客户打开后失效 (一次性)</li>
 *   <li>或 max_clicks=1 限制点击次数</li>
 *   <li>expire_at 默认 24h</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LinkService {

    private final ChatLinkMapper linkMapper;

    /** 白名单域名 (yml 配置) */
    @Value("${cs.link.allowed-domains:example.com,example.org}")
    private String allowedDomainsCsv;

    /** 默认有效期 (小时) */
    @Value("${cs.link.default-expire-hours:24}")
    private int defaultExpireHours;

    /** 一次性 token */
    @Value("${cs.link.one-time:true}")
    private boolean oneTime;

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final Set<String> BLOCKED_HOSTS = Set.of(
        "localhost", "127.0.0.1", "0.0.0.0", "169.254.169.254"
    );

    private static final Set<String> PRIVATE_IP_PREFIXES = Set.of(
        "192.168.", "10.", "172.16.", "172.17.", "172.18.", "172.19.",
        "172.20.", "172.21.", "172.22.", "172.23.", "172.24.", "172.25.",
        "172.26.", "172.27.", "172.28.", "172.29.", "172.30.", "172.31."
    );

    /**
     * 创建一条链接记录
     *
     * @param sessionId    会话 ID
     * @param agentUsername 坐席
     * @param targetUrl    客户要跳转的 URL
     * @return 短 token
     */
    public ChatLink create(Long sessionId, String agentUsername, String targetUrl) {
        // 1) URL 校验
        validateUrl(targetUrl);

        // 2) 生成 token
        byte[] buf = new byte[24];
        RANDOM.nextBytes(buf);
        String shortToken = Base64.getUrlEncoder().withoutPadding().encodeToString(buf);

        // 3) 写库
        ChatLink link = new ChatLink();
        link.setSessionId(sessionId);
        link.setAgentUsername(agentUsername);
        link.setTargetUrl(targetUrl);
        link.setShortToken(shortToken);
        link.setClickCount(0);
        link.setMaxClicks(oneTime ? 1 : 0); // 0 = 不限
        link.setExpireAt(LocalDateTime.now().plusHours(defaultExpireHours));
        link.setRevoked(0);
        link.setCreatedAt(LocalDateTime.now());
        linkMapper.insert(link);
        log.info("[Link] create link id={} session={} agent={} url={}",
            link.getId(), sessionId, agentUsername, targetUrl);
        return link;
    }

    /**
     * 客户打开短链时校验并自增计数
     */
    public ChatLink resolve(String shortToken) {
        ChatLink link = linkMapper.findFirstByToken(shortToken);
        if (link == null) throw new ApiException(404, "链接不存在");
        if (link.getRevoked() != null && link.getRevoked() == 1) {
            throw new ApiException(410, "链接已撤销");
        }
        if (link.getExpireAt().isBefore(LocalDateTime.now())) {
            throw new ApiException(410, "链接已过期");
        }
        if (link.getMaxClicks() != null && link.getMaxClicks() > 0
                && link.getClickCount() != null
                && link.getClickCount() >= link.getMaxClicks()) {
            throw new ApiException(410, "链接已达到最大点击次数");
        }
        // 自增
        link.setClickCount(link.getClickCount() + 1);
        if (oneTime) {
            link.setRevoked(1); // 一次性的立即撤销
        }
        linkMapper.updateById(link);
        return link;
    }

    /**
     * URL 校验: 黑名单 + 白名单
     */
    private void validateUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new ApiException(400, "URL 不能为空");
        }
        if (url.length() > 2000) {
            throw new ApiException(400, "URL 太长");
        }

        URI uri;
        try {
            uri = URI.create(url);
        } catch (Exception e) {
            throw new ApiException(400, "URL 格式错误");
        }

        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equals("http") || scheme.equals("https"))) {
            throw new ApiException(400, "只允许 http/https");
        }

        String host = uri.getHost();
        if (host == null) throw new ApiException(400, "URL 缺 host");

        String hostLower = host.toLowerCase();

        // 黑名单
        if (BLOCKED_HOSTS.contains(hostLower)) {
            throw new ApiException(403, "禁止访问该域名");
        }
        for (String prefix : PRIVATE_IP_PREFIXES) {
            if (hostLower.startsWith(prefix)) {
                throw new ApiException(403, "禁止访问内网地址");
            }
        }
        if (hostLower.endsWith(".gov") || hostLower.endsWith(".gov.cn")) {
            throw new ApiException(403, "政府域名需审批");
        }

        // 白名单
        if (allowedDomainsCsv != null && !allowedDomainsCsv.isBlank()) {
            Set<String> allowed = Set.of(allowedDomainsCsv.toLowerCase().split(","));
            boolean ok = allowed.stream().anyMatch(d -> hostLower.equals(d) || hostLower.endsWith("." + d));
            if (!ok) {
                throw new ApiException(403, "域名不在白名单: " + hostLower);
            }
        }
    }
}