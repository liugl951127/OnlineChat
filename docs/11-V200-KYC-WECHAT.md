# v2.0.0 完整 KYC + 微信小程序 + 公众号 H5

## 1. KYC 完整流程（v2.0.0）

### 业务定义
**KYC (Know Your Customer)** 是金融客户身份识别的全套流程，监管要求每个客户开户/购买金融产品前必须通过。

### 7 步状态机
```
INIT
  ↓ 1. 身份证 OCR 上传（识别姓名/号码/有效期）
OCR_UPLOADED
  ↓ 2. 活体检测（张嘴/眨眼/摇头）
LIVENESS_PASSED
  ↓ 3. 人脸比对（身份证照 vs 活体照）
FACE_MATCHED
  ↓ 4. 视频双录（朗读风险声明）
VIDEO_RECORDED
  ↓ 5. 提交坐席审核
AUDITING
  ↓ 6. 审核通过 → APPROVED（or REJECTED）
APPROVED
  ↓ 7. 银行卡四要素鉴权
BANK_BINDING
  ↓ 完成
COMPLETED
```

任何环节失败 → `REJECTED`，客户需重新提交。

### 数据库表（Flyway V4.0.0）
- `kyc_application` — KYC 申请单
- `kyc_risk_statement` — 风险声明库（5 段预置监管要求）
- `kyc_video_record` — 双录视频记录（分段）
- `bank_card` — 客户绑卡
- `kyc_blacklist` — 黑名单

### Mock 算法服务（生产可对接真实厂商）
| 服务 | Mock 实现 | 生产替换 |
|------|-----------|----------|
| OcrService | 返回预置姓名/号码/地址 | 百度 OCR / 阿里云 OCR / 腾讯云 OCR |
| LivenessService | 90% 通过率，100ms 处理 | 腾讯云慧眼 / 阿里云实人认证 |
| FaceMatchService | 88-98 相似度 | 百度人脸识别 / FaceNet |
| VideoRecordService | 返回 OSS 占位 URL + 校验和 | 阿里云 OSS + ffmpeg 抽帧 |
| BankCardVerifyService | 100% 通过 | 银联 / 网联四要素 API |
| RiskControlService | 含 9999 视为黑名单 | 百融 / 同盾 / 银联反欺诈 |

接口签名保持稳定，方便后续无缝切换。

### 集成拦截

`ComplianceService` 把 `isPhoneVerified` 替换为 `kycService.isCompleted`：
- 购买金融产品 → 必须 KYC_COMPLETED
- 否则合规检查第 1 项失败 → REJECTED

### REST API
```
POST /im/kyc/create            创建申请单
POST /im/kyc/upload-idcard     身份证 OCR
POST /im/kyc/liveness          活体检测（{ faceImgUrl, actions }）
POST /im/kyc/face-match        人脸比对
POST /im/kyc/submit-video      视频双录（{ statementCode, videoBase64, durationSec }）
POST /im/kyc/submit-audit      提交审核
POST /im/kyc/bind-card         银行卡四要素（{ cardNo, cardName, idCardNo, mobile }）
GET  /im/kyc/my                我的最新申请
GET  /im/kyc/status            { status, completed }
GET  /im/kyc/statements        风险声明列表（公开）
GET  /im/kyc/audit/queue       坐席：待审核
GET  /im/kyc/audit/mine        坐席：我的审核历史
POST /im/kyc/audit/{no}/approve
POST /im/kyc/audit/{no}/reject
```

### 风险声明库（5 段预置）
- RS-INVEST-001 投资者适当性声明（15 秒）
- RS-INVEST-002 资金来源合法声明（12 秒）
- RS-INSURE-001 投保意愿声明（10 秒）
- RS-GENERAL-001 信息真实性声明（8 秒）
- RS-FUND-001 基金交易特别声明（15 秒）

## 2. 微信小程序（v2.0.0）

### 项目位置
`cs-mini-program/` — 微信原生小程序（支持 uni-app 迁移）

### 登录流程
```
小程序 wx.login() → 拿 jsCode
POST /auth/wx-mini/login { jsCode, encryptedData?, iv? }
  → 后端调微信 jscode2session 拿 openid
  → 查/建用户（provider=WECHAT_MINI）
  → 颁发 JWT
```

### 页面
- `pages/index/index` 首页（4 大功能入口）
- `pages/chat/chat` 聊天页（HTTP 轮询 3 秒）
- `pages/kyc/kyc` KYC 7 步流程
- `pages/login/login` 登录

### 调后端
通过 `app.js` 的 `app.request()` 统一封装，自动加 Bearer token。

### 接口
```js
app.request({ url: '/im/kyc/my', method: 'POST' })
app.request({ url: '/im/customer/chat', data: { text } })
```

## 3. 微信公众号 H5（v2.0.0）

### 入口
**GET /wx-oa-h5.html** — 微信公众号专属入口页
- 蓝色按钮"🔐 微信授权登录" → `/auth/wx-oa/h5-entry`
- 灰色按钮"👤 访客模式体验" → 静默登录

### OAuth 流程
```
1. 公众号菜单配置跳转到 /auth/wx-oa/h5-entry
2. 后端重定向到微信授权页：https://open.weixin.qq.com/connect/oauth2/authorize
3. 用户授权后微信回调 → /auth/wechat-oa/callback?code=xxx
4. 后端用 code 换 openid + 用户信息 → 颁发 JWT
5. 重定向到 /#/customer?token=xxx&customerId=xxx&from=wx-oa
6. 前端 Vue SPA 自动加载客户聊天页
```

### CSRF 白名单
`CsrfFilter` 已加 `/auth/wx-mini/login` + `/auth/wx-oa/h5-entry` 到白名单，避免跨域问题。

## 4. 前端 KYC 入口（v2.0.0）

### Customer.vue 改造
- **顶部状态条**：未开始 / 审核中 / 已完成（4 种颜色）
- **工具栏按钮**：🔐 实名认证 / ✓ 已认证
- **弹窗**：KycFlow.vue（7 步骤 + MediaRecorder 视频录制）

### MediaRecorder API
```js
navigator.mediaDevices.getUserMedia({ video: true, audio: true })
new MediaRecorder(stream).start()
// 录完 .webm → 转 base64 → 上传
```
无摄像头权限时自动降级 Mock 录制 15 秒。

## 5. 测试

| 测试 | 结果 |
|------|------|
| KycIntegrationTest（4 cases） | 4/4 ✅ |
| WxMiniIntegrationTest（3 cases） | 3/3 ✅ |
| FinancialOrderIntegrationTest | 5/5 ✅ |
| TicketIntegrationTest | 4/4 ✅ |
| AuthMysqlIntegrationTest | 5/5 ✅ |
| OAuthLoginTest | 9/9 ✅ |
| AuthEndToEndTest | 4/4 ✅ |
| SecurityFilterTest | 14/14 ✅ |
| AuthLoginTest | 10/12（lockUntil 遗留） |

**总计 58/61 = 95% PASS**

## 6. 文件清单

### 后端新增
```
cs-im/src/main/java/com/example/im/
  domain/KycApplication.java
  domain/KycRiskStatement.java
  domain/KycVideoRecord.java
  domain/BankCard.java
  kyc/OcrService.java
  kyc/LivenessService.java
  kyc/FaceMatchService.java
  kyc/VideoRecordService.java
  kyc/BankCardVerifyService.java
  kyc/RiskControlService.java
  service/KycService.java
  controller/KycController.java
  repo/KycApplicationMapper.java
  repo/KycRiskStatementMapper.java
  repo/KycVideoRecordMapper.java
  repo/BankCardMapper.java
  resources/mapper/KycApplicationMapper.xml
  resources/mapper/KycRiskStatementMapper.xml
  resources/mapper/KycVideoRecordMapper.xml
  resources/mapper/BankCardMapper.xml
  resources/db/migration/V4.0.0__kyc.sql

cs-auth/src/main/java/com/example/auth/
  service/WxMiniService.java
  resources/db/migration/V2.0.1__wechat_user_channel.sql
```

### 前端新增
```
cs-frontend/src/components/KycFlow.vue        # 7 步 KYC 流程
cs-frontend/src/views/Customer.vue           # 加 KYC 入口和状态条
cs-gateway/src/main/resources/static/wx-oa-h5.html  # 公众号 H5 入口
```

### 小程序
```
cs-mini-program/
  app.js / app.json / sitemap.json
  pages/index/index.{js,wxml,wxss,json}
  pages/chat/chat.{js,wxml,wxss,json}
  pages/kyc/kyc.{js,wxml,wxss,json}
```

## 7. 后续集成路线

### 真实 OCR
```java
AipOcr client = new AipOcr(APP_ID, API_KEY, SECRET_KEY);
JSONObject res = client.idcard(frontImg, backImg, options);
```

### 真实活体 + 人脸比对
```java
TencentCloudFactory factory = new TencentCloudFactory(...);
HivisionClient client = factory.hivision();
DetectFaceResult result = client.detectFace(videoBytes, "MOUTH");
```

### 真实视频存储
```java
OSSClient oss = new OSSClient(endpoint, accessKey, secret);
oss.putObject(bucket, "kyc-video/" + uuid + ".webm", inputStream);
String url = oss.generatePresignedUrl(bucket, key, expireTime).toString();
```

### 真实银行卡四要素
```java
UnionPayClient client = new UnionPayClient(merchantId, cert);
Verify4ElementsRequest req = new Verify4ElementsRequest(cardNo, name, idCard, mobile);
Verify4ElementsResponse resp = client.verify4Elements(req);
```

接口签名已经抽象成 4 个独立 Service 类（OcrService / LivenessService / FaceMatchService / BankCardVerifyService），
后续只需替换实现类，不影响业务逻辑。