# 部署指南（One-Shot Deployment）

OnlineChat 提供**一件部署**支持：单一站点同时托管客户、坐席、后管三个入口。

## 一件部署

### URL 路由表

| URL | 角色 | 说明 |
|---|---|---|
| `/` 或 `/app` | 引导页 | 4 卡片选择角色（可点击直接进入） |
| `/app?role=customer` | 客户 | 跳转 `/customer/` |
| `/app?role=agent` | 坐席 | 跳转 `/agent/` |
| `/app?role=admin` | 后管 | 跳转 `/admin/` |
| `/customer/` | 客户工作台 | 智能客服 + 人工坐席 |
| `/agent/` | 坐席工作台 | 排队 + 富文本聊天 |
| `/admin/` | 后管系统 | 审计 + 数据看板 |
| `/login/` | 登录 | 4 种方式：账号密码 / 手机号 / OAuth / 静默 |
| `/shared/design-system.css` | 共享样式 | CSS 变量令牌 |
| `/shared/bootstrap.js` | 共享脚本 | token 提取 + 401 全局处理 |

### 一件访问（推荐）

```
# 引导页
https://yourdomain.com/

# 直接进入客户页（带 token）
https://yourdomain.com/app?role=customer&token=eyJhbGc...

# 直接进入坐席页
https://yourdomain.com/app?role=agent&token=eyJhbGc...
```

`/app?role=xxx&token=xxx` 会：
1. 把 token 存入 `localStorage.cs_token`
2. 清理 URL（去掉 `token` 参数）
3. 重定向到 `/customer/` 或 `/agent/` 或 `/admin/`

## 单端口部署

整个项目只需要暴露 cs-gateway 一个端口（默认 9000）：

```
cs-gateway:9000  ←  唯一对外端口
├─ /            → 引导页
├─ /login/      → 登录页
├─ /app         → 角色选择
├─ /customer/   → 客户页
├─ /agent/      → 坐席页
├─ /admin/      → 后管页
├─ /api/auth/*  → 认证服务（via Feign）
├─ /api/im/*    → 即时通信服务
├─ /api/robot/* → 机器人服务
└─ /api/trade/* → 交易服务
```

## 反向代理（Nginx 推荐）

```nginx
server {
    listen 443 ssl;
    server_name yourdomain.com;

    location / {
        proxy_pass http://127.0.0.1:9000;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        # WebSocket 支持
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_read_timeout 86400;
    }

    # 静态资源缓存
    location ~* \.(css|js|png|jpg|jpeg|gif|ico|svg|woff2?)$ {
        proxy_pass http://127.0.0.1:9000;
        expires 7d;
        add_header Cache-Control "public, immutable";
    }
}
```

## Docker 一件部署

```bash
# 1. 启动依赖（MySQL + Redis + Nacos）
docker-compose up -d mysql redis nacos

# 2. 启动业务服务
docker-compose up -d cs-auth cs-robot cs-im cs-trade cs-gateway

# 3. 一件访问
open http://localhost:9000/
```

## OAuth 回调 URL 配置

第三方登录回调必须配置为：

```
GitHub OAuth App:
  Authorization callback URL: https://yourdomain.com/auth/github/callback

Google OAuth Client:
  Authorized redirect URIs:  https://yourdomain.com/auth/google/callback

WeChat Work:
  OAuth redirect domain:     yourdomain.com

WeChat OA:
  Authorization callback:    https://yourdomain.com/auth/wechat-oa/callback
```

## 共享资源

| 资源 | 路径 | 说明 |
|---|---|---|
| CSS 变量 | `/shared/design-system.css` | 颜色/字号/间距/阴影令牌 |
| Bootstrap | `/shared/bootstrap.js` | token 处理 + 401 拦截 + Toast |
| 设计原则 | 暗色主题 + 现代卡片 + 微动画 |

新页面只需 `<link rel="stylesheet" href="/shared/design-system.css">` + `<script src="/shared/bootstrap.js"></script>` 即可获得统一视觉。

## 测试访问

```bash
# 默认账号
admin / admin                → 后管

# 静默登录
open http://localhost:9000/login/

# 手机号登录（Mock 模式看 debugCode）
open http://localhost:9000/login/

# GitHub OAuth（Mock 模式）
http://localhost:9000/auth/github/authorize?redirect_uri=http://localhost:9000/auth/github/callback
```

## 升级流程

1. `git pull origin master`
2. `mvn clean package -DskipTests`
3. `docker-compose restart`
4. 浏览器**清缓存**（避免 design-system.css 缓存导致样式错位）

## 性能优化

- 静态资源 CDN 缓存（`/shared/*`、`/login/*` 等可设 7 天）
- 设计系统 CSS 体积 < 10KB（gzip 后 3KB）
- 无构建链：HTML 直接 `vue.global.prod.js` CDN 引入，省下 webpack 体积
- 单页首屏 < 1.5s（CDN 加载后）

## 多域名部署（可选）

如果客户/坐席/后管需要分开域名：

```nginx
server { server_name customer.example.com; proxy_pass http://127.0.0.1:9000/customer/; }
server { server_name agent.example.com;    proxy_pass http://127.0.0.1:9000/agent/;    }
server { server_name admin.example.com;    proxy_pass http://127.0.0.1:9000/admin/;    }
```

代码无需任何修改。