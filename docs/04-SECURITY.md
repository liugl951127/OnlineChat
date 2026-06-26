# 安全防护说明（10 大类）

OnlineChat v1.7.0 实施**多层防御**安全策略，覆盖 OWASP Top 10 + 国内等保 2.0 要求。

## 防御一览

| 序号 | 攻击类型 | 防御组件 | 实现位置 |
|---|---|---|---|
| 1 | **XSS 跨站脚本** | DOMPurify（前端）+ SecurityHeaders CSP + Sanitizer（后端） | 前端 `request.js#safeText` / 后端 `Sanitizer.java` |
| 2 | **CSRF 跨站请求伪造** | CsrfFilter（双提交 Cookie + 32 字节 Token） | `CsrfFilter.java` |
| 3 | **SQL 注入** | MyBatis 参数化 + SqlInjectionFilter 兜底 | `SqlInjectionFilter.java` |
| 4 | **点击劫持** | X-Frame-Options: DENY | `SecurityHeadersFilter.java` |
| 5 | **MIME 嗅探** | X-Content-Type-Options: nosniff | `SecurityHeadersFilter.java` |
| 6 | **HTTPS 降级** | Strict-Transport-Security | `SecurityHeadersFilter.java` |
| 7 | **Referer 泄漏** | Referrer-Policy: strict-origin-when-cross-origin | `SecurityHeadersFilter.java` |
| 8 | **文件上传攻击** | 类型/MIME/魔数/大小/路径穿越 五重检查 | `FileUploadSecurityFilter.java` |
| 9 | **请求重放** | X-Request-Time 时间戳窗口校验 | `RequestReplayFilter.java` |
| 10 | **暴力破解** | RateLimiter（限流）+ 账号锁定（5 次失败锁 15 分钟） | `RateLimiter.java` / `AuthService` |

## 1. XSS 防御

**前端**：
```javascript
import DOMPurify from 'dompurify'
export function safeText(html) {
  return DOMPurify.sanitize(html, {
    ALLOWED_TAGS: ['b', 'i', 'u', 'strong', 'em', 'br', 'p', 'span'],
    ALLOWED_ATTR: []
  })
}
```

**所有用户输入都过 `safeText()`**：客户名 / 消息内容 / 文件名 / 头像 URL。

**后端**：
- `Sanitizer.java` 检测 `<script>` / `javascript:` / `onerror=` 等
- CSP 头禁止 inline script（开发环境暂留 unsafe-inline）

**测试**：`SecurityFilterTest.xss_*`

## 2. CSRF 防御

**双提交 Cookie 模式**：

1. 登录成功 → 后端 `CsrfTokenIssuer.issue()` 生成 32 字节随机 Token
2. 写入 Cookie `CSRF-TOKEN`（HttpOnly=false，7 天过期）
3. 同时返回到响应体 `csrf` 字段
4. 前端存 `localStorage.cs_csrf`
5. 写操作请求带 Header `X-CSRF-Token`
6. `CsrfFilter` 校验：Header == Cookie + Referer 同源

**白名单**（无需 CSRF）：
- `/auth/login`、`/auth/login-phone`、`/auth/register`
- `/auth/{wechat-oa,wechat-work,github,google}/callback`（OAuth 流程自验 state）
- `/auth/admin/login`

**测试**：`SecurityFilterTest.csrf_*`

## 3. SQL 注入防御

**第一道**：MyBatis JPA 参数化查询（`@Query` / `findByXxx`），永不拼接 SQL。

**第二道**：`SqlInjectionFilter` 兜底，检测 query/form 参数中的攻击特征：
- `UNION SELECT`、`DROP TABLE`、`OR 1=1`
- `--` 注释、`xp_cmdshell`、`BENCHMARK`
- `<script>`、`javascript:`、`onload=`、`eval(`

正则匹配通过即拒绝（400）。**生产应同时启用 WAF**（如 ModSecurity）。

## 4. 文件上传防御

`FileUploadSecurityFilter` 五重检查：

| 检查 | 说明 | 失败处理 |
|---|---|---|
| **Content-Type** | 仅 image/png、jpeg、gif、webp、pdf、text/plain | 400 |
| **扩展名** | 白名单 + 黑名单（拒 jsp/php/exe/jar/svg） | 400 |
| **大小** | ≤ 5 MB | 400 |
| **文件名** | 禁止 `..`、`/`、`\`（路径穿越） | 400 |
| **魔数** | 前 4 字节比对（PNG: 89504E47 / JPEG: FFD8FF 等） | 400 |

**生产增强**：接 ClamAV 病毒扫描 + 对象存储隔离桶。

## 5. 请求重放防御

写操作必须带 `X-Request-Time` 头：
```javascript
config.headers['X-Request-Time'] = Date.now().toString()
```

后端校验与服务器时间差 ≤ 5 分钟。**生产应加 Redis 一次性消费**：相同时间戳 + 用户只能成功一次。

## 6. HTTP 安全响应头

`SecurityHeadersFilter` 自动加：

```http
X-Frame-Options: DENY                            # 防点击劫持
X-Content-Type-Options: nosniff                  # 防 MIME 嗅探
Strict-Transport-Security: max-age=31536000     # 强制 HTTPS
Referrer-Policy: strict-origin-when-cross-origin # 防 Referer 泄漏
Permissions-Policy: geolocation=(), microphone=() # 禁用危险 API
Content-Security-Policy: default-src 'self'      # 限制资源加载源
X-XSS-Protection: 1; mode=block                 # 旧浏览器兼容
```

## 7. 限流（防爆破）

`RateLimiter` 滑动窗口，按 key 限流：
- `login-pwd:{username}:{ip}`：1 分钟内 5 次
- `login-phone:{phone}`：1 分钟内 3 次
- `sms-code:{phone}`：1 分钟 1 次
- `upload:{userId}`：1 分钟 5 次

**生产建议**：用 Redis 集群替代内存版（已预留接口）。

## 8. 密码安全

- **PBKDF2-HMAC-SHA256**：10 万次迭代 + 16 字节随机 Salt
- **AES-ECB**：手机号加密（key 派生自 SHA-256）
- **5 次失败锁 15 分钟**

## 9. JWT 安全

- HS256 签名，密钥 ≥ 32 字符
- TTL 24 小时
- Redis 黑名单支持（强制下线）
- 生产强制 HTTPS（避免 token 嗅探）

## 10. 等保 2.0 合规

| 等保要求 | 实现 |
|---|---|
| 身份鉴别 | 5 种登录方式 + JWT + Token 黑名单 |
| 访问控制 | RBAC（角色：CUSTOMER / AGENT / ADMIN）|
| 安全审计 | `AuditLog` + `AuditLogAspect` 全操作记录 |
| 数据完整性 | HMAC-SHA256 消息签名（防中间人篡改）|
| 数据保密性 | 手机号 AES + 敏感信息脱敏（`SensitiveUtils`）|
| 通信保密 | TLS 1.3（HSTS 强制）|
| 备份恢复 | 数据库每日快照 + Redis 持久化 |

## 安全测试

`SecurityFilterTest` 14 用例：
- SecurityHeaders 完整
- CSRF 缺失/不匹配/白名单/长度不足
- SQL 注入 UNION/OR 1=1
- XSS script/javascript:/onerror
- 重放时间漂移
- CSRF Token 生成

## 报告漏洞

邮箱：security@example.com
PGP Key：https://yourdomain.com/.well-known/pgp-key.txt

**负责任披露**：90 天内不公开，期间修复 + 致谢。