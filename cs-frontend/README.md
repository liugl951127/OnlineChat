# OnlineChat Frontend (Vue 3 + Element Plus)

> 一件部署的现代前端工程，替代旧版原生 HTML/Vue.global。

## 技术栈

| 依赖 | 版本 | 作用 |
|---|---|---|
| Vue | 3.4 | 渐进式 UI 框架 |
| Vite | 5.2 | 构建工具（开发服务器 + 生产打包） |
| Element Plus | 2.7 | UI 组件库 |
| Pinia | 2.1 | 状态管理 |
| Vue Router | 4.3 | 路由 |
| Axios | 1.7 | HTTP 客户端 |
| DOMPurify | 3.1 | XSS 清理 |

## 项目结构

```
cs-frontend/
├── public/                静态资源
├── src/
│   ├── api/               HTTP 封装
│   │   ├── request.js     Axios 拦截器 + CSRF + 防注入
│   │   └── index.js       业务 API（auth/im/agent/admin/robot）
│   ├── views/             页面（4 个）
│   │   ├── Home.vue       引导页
│   │   ├── Login.vue      登录页
│   │   ├── Customer.vue   客户工作台
│   │   ├── Agent.vue      坐席工作台
│   │   └── Admin.vue      后管系统
│   ├── router/            路由 + 守卫
│   ├── store/             Pinia Store
│   ├── utils/             工具函数
│   └── styles/            SCSS 主题
├── index.html             HTML 模板（含 CSP 头）
├── vite.config.js
└── package.json
```

## 开发

```bash
# 安装依赖
npm install

# 启动 dev server（5173 端口）
npm run dev

# 生产构建
npm run build   # 输出到 dist/

# 测试
npm test

# Lint
npm run lint
```

## 一件部署

构建产物 `dist/` 直接拷贝到 `cs-gateway/src/main/resources/static/`，重启网关即可。

```bash
npm run build
cp -r dist/* ../cs-gateway/src/main/resources/static/
```

访问：`https://yourdomain.com/` → 自动路由到对应角色页面。

## 安全特性（前端 7 大防线）

1. **DOMPurify XSS 过滤**：所有用户输入输出过 `safeText()`
2. **CSRF Token**：双提交 Cookie + Header，登录后由后端下发
3. **请求时间戳**：写操作带 `X-Request-Time` 防重放
4. **URL 参数清理**：query 参数去除 `<>"'%;()&+`
5. **CSP 头**：HTML 模板内嵌严格 CSP
6. **路由守卫**：未登录跳 `/login?next=`
7. **文件上传白名单**：前端 + 后端双校验 MIME / 扩展名 / 大小 / 魔数

## 设计系统

CSS 变量集中在 `src/styles/index.scss`：
- 颜色：`--el-color-primary` 等 Element Plus 主题覆盖
- 间距 / 字号 / 圆角 / 阴影全部令牌化
- 暗色模式预留（切换 `--el-color-*` 即可）

## 浏览器兼容

- Chrome / Edge ≥ 90
- Safari ≥ 14
- Firefox ≥ 90
- 移动端：iOS Safari ≥ 14 / Chrome Android ≥ 90

## 升级 Element Plus

```bash
npm update element-plus
npm run build
```

主题变量集中在 SCSS，重构成本低。