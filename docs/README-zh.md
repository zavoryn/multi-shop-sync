# Multi-Shop Sync · 中文文档

> 基于策略模式 + 工厂模式 + 模板方法，统一管理 **抖音小店 / 小红书 / 微信小店 / 本地商城** 的商品上下架与状态流转。

[English](../README.md) | **中文**

---

## 为什么需要这个项目

电商多渠道运营场景下，把同一商品发布到多个平台，三大痛点：

| 痛点 | 表现 |
|------|------|
| **API 差异大** | 抖音 HMAC-SHA256、小红书 MD5、微信 Access Token 三套鉴权完全不同 |
| **状态难统一** | 各平台异步审核，本地状态机要兼容三种回调事件类型 |
| **重复开发高** | 接一个新平台就要重写一套推送 + 状态同步 + 回调验签 |

本项目通过设计模式封装：
- **核心业务与渠道完全解耦** —— 新增渠道只需 3 步
- **统一状态机** —— DRAFT / WAIT_PLATFORM_AUDIT / ON_SHELF / AUDIT_REJECT / OFF_SHELF
- **开箱即用** —— H2 内存库 + dev profile 一键启动，无需准备 MySQL

---

## 实现进度（10/10 Phase 全部完成）

| Phase | 内容 | 关键提交 |
|-------|------|---------|
| 0 | MyBatis-Plus + H2/MySQL 双 profile + Caffeine 依赖 | `ee16a5d` |
| 1 | WebhookController 路由冲突修复 + @Async 异步入口 | `c7659f0` |
| 2 | 4 张表持久层 + Strategy 接口区分 outProductId / channelProductId | `4be1c99` |
| 3 | ProductStatusMachine 状态机校验，杜绝非法转移 | `886f263` |
| 4 | AccessTokenManager + Caffeine + 单飞 + 定时预刷新 | `3403355` |
| 5 | 三家 doSyncPlatformStatus 完整状态字段映射 | `2dc8dc1` |
| 6 | 微信 AES-CBC 解密 + sha1 验签（WechatCryptoUtil） | `c92b856` |
| 7 | 异常分类 + Resilience4j 限流 + GlobalExceptionHandler | `e4c5cbe` |
| 8 | 69 个单测全绿（含 100 线程并发单飞验证） | `29abcda` |
| 9 | 配置 / 文档收尾 | （本 commit） |

详细路线图：[IMPLEMENTATION-PLAN.md](IMPLEMENTATION-PLAN.md)
平台状态字段对照表：[platform-status-mapping.md](research/platform-status-mapping.md)

---

## 快速开始

### 1. 准备

- JDK 1.8+
- Maven 3.6+
- （可选）MySQL 8 —— 不需要，dev profile 自带 H2 内存库

### 2. 启动 (dev profile，零配置)

```bash
mvn spring-boot:run
```

`application.yml` 默认 `spring.profiles.active=dev`，自动用 H2 内存库 + 自动建表 + 加载示例类目映射。
启动后访问 [http://localhost:8080/h2-console](http://localhost:8080/h2-console) 可看 4 张表。

### 3. 切到 prod profile（接 MySQL）

```bash
export DB_HOST=127.0.0.1
export DB_PORT=3306
export DB_NAME=multi_shop_sync
export DB_USER=root
export DB_PASSWORD=***

mvn spring-boot:run -Dspring-boot.run.profiles=prod
```

生产环境的 schema 建议由 DBA 或 flyway/liquibase 管理，prod profile 不会自动建表。

### 4. 接入真实渠道

在 `application.yml` 把对应渠道改成 `enabled: true`，并通过环境变量注入凭证：

```bash
export DOUYIN_APP_KEY=xxx
export DOUYIN_APP_SECRET=xxx
export DOUYIN_REFRESH_TOKEN=xxx
```

`enabled: true` 时对应渠道的 `RefreshStrategy` 会自动装载，token 由 `CaffeineTokenManager` 缓存 + 定时刷新。

### 5. 调用示例

**推送本地商品**（无需任何外部凭证，dev 环境直接可用）：

```bash
curl -X POST http://localhost:8080/api/product/push \
  -H "Content-Type: application/json" \
  -d '{
    "channelCode": "local",
    "product": {
      "outProductId": "P-001",
      "title": "测试商品",
      "categoryId": 1001,
      "mainImages": ["https://x/1.jpg"],
      "skus": [{
        "skuCode": "SKU-001",
        "price": 9900,
        "stock": 100,
        "attributes": {"颜色": "红色"}
      }]
    }
  }'
```

**上下架**：

```bash
curl -X POST http://localhost:8080/api/product/changeStatus \
  -H "Content-Type: application/json" \
  -d '{"channelCode":"local","outProductId":"P-001","status":"OFF_SHELF"}'
```

**Webhook**（各平台后台配置此 URL）：

```
POST https://your-domain/api/webhook/douyin
POST https://your-domain/api/webhook/xiaohongshu
POST https://your-domain/api/webhook/wechat   ← 自动 AES-CBC 解密 + sha1 验签
```

---

## API 错误码

| HTTP | 触发场景 |
|------|---------|
| 400 | 参数校验失败 / 渠道不支持 / 商品未推送 |
| 401 | 渠道鉴权失败（token 过期、签名错误） |
| 409 | 状态机非法转移（如 OFF_SHELF → AUDIT_REJECT） |
| 422 | 平台业务错误（类目不存在、SKU 冲突等） |
| 503 | 上游不可用（已重试 3 次仍失败） |
| 500 | 内部错误 + traceId（去日志查这个 ID） |

---

## 测试

```bash
mvn test
```

当前覆盖：69 个用例，包括签名算法回归基线、状态机合法/非法转移、Caffeine 100 线程并发单飞、AES round-trip 等。

---

## 贡献新渠道

参考英文 README 的 "Add a New Channel (3 Steps)"。本框架核心抽象稳定，新增渠道**不需要改任何现有代码**。

详见 [CONTRIBUTING.md](../CONTRIBUTING.md)。
